package gov.usgs.volcanoes.swarm.data.seedlink;

import gov.usgs.volcanoes.core.data.HelicorderData;
import gov.usgs.volcanoes.core.data.Wave;
import gov.usgs.volcanoes.core.time.J2kSec;
import gov.usgs.volcanoes.swarm.ChannelUtil;
import gov.usgs.volcanoes.swarm.data.CachedDataSource;
import gov.usgs.volcanoes.swarm.data.DataSourceType;
import gov.usgs.volcanoes.swarm.data.Gulper;
import gov.usgs.volcanoes.swarm.data.GulperList;
import gov.usgs.volcanoes.swarm.data.GulperListener;
import gov.usgs.volcanoes.swarm.data.SeismicDataSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of <code>SeismicDataSource</code> that connects to an
 * SeedLink Server.
 * 
 * @author Kevin Frechette (ISTI)
 * @author Tom Parker
 */
public class SeedLinkSource extends SeismicDataSource {
  /** The logger. */
  private static final Logger LOGGER = LoggerFactory.getLogger(SeedLinkSource.class);

  /** The gulp delay. */
  public static final int GULP_DELAY = 1000;

  /** The gulp size. */
  private static final int GULP_SIZE = 60;

  // /** Info string prefix text or null if none. */
  private static final String INFO_FILE_TEXT =
      System.getProperty(DataSourceType.getShortName(SeedLinkSource.class) + "infofile");

  /** The server host. */
  private String host;

  /** The information string File or null if none. */
  private File infoStringFile;

  /** The colon separated parameters. */
  private String params;

  /** The server port. */
  private int port;

  /** SeedLink client list. */
  private final List<SeedLinkClient> seedLinkClientList;

  /** time of last gulped data access. */
  private Map<String, SeedLinkGulperListener> gulperListeners;

  /**
   * Default constructor.
   */
  public SeedLinkSource() {
    LOGGER.debug("Constructing new seedlink source");
    seedLinkClientList = new ArrayList<SeedLinkClient>();
    gulperListeners = new HashMap<String, SeedLinkGulperListener>();

  }

  /**
   * Create a SeedLink server source.
   * 
   * @param s the colon separated parameters.
   */
  public SeedLinkSource(String name, String s) {
    this();
    LOGGER.debug("Constructing new seedlink source2");
    this.name = name;
    parse(s);
  }


  /**
   * Parse config string.
   */
  public void parse(String params) {
    this.params = params;
    String[] ss = params.split(":");
    int ssIndex = 0;
    host = ss[ssIndex++];
    port = Integer.parseInt(ss[ssIndex]);
    if (INFO_FILE_TEXT != null) {
      infoStringFile = new File(INFO_FILE_TEXT + host + port + ".xml");
    }
  }

  /**
   * Close the data source.
   */
  public void close() {
    // close clients
    synchronized (seedLinkClientList) {
      if (seedLinkClientList.size() != 0) {
        LOGGER.debug("close the data source");
        for (SeedLinkClient client : seedLinkClientList) {
          client.close();
        }
        seedLinkClientList.clear();
      }
    }
  }


  public Gulper createGulper(GulperList gl, String k, String ch, double t1,
      double t2, int size, int delay) {
    return new SeedLinkGulper(gl, k, this, ch, t1, t2, size, delay);
  }

  /**
   * Get the channels. 
   * 
   * @return the list of channels.
   */
  public List<String> getChannels() {
    String infoString = readChannelCache();

    if (infoString == null) {
      final SeedLinkClient client = createClient();
      infoString = client.getInfoString();
      removeClient(client);
      writeChannelCache(infoString);
    }

    List<String> channels = Collections.emptyList();
    if (!(infoString == null || infoString.isEmpty())) {
      try {
        SeedLinkChannelInfo seedLinkChannelInfo = new SeedLinkChannelInfo(this, infoString);
        channels = seedLinkChannelInfo.getChannels();
      } catch (Exception ex) {
        LOGGER.error("Cannot parse station list", ex);
      }
    }

    ChannelUtil.assignChannels(channels, this);
    return Collections.unmodifiableList(channels);
  }


  private String readChannelCache() {
    String infoString = null;

    if (infoStringFile != null && infoStringFile.canRead()) {
      FileInputStream stream = null;
      try {
        stream = new FileInputStream(infoStringFile);
        FileChannel fc = stream.getChannel();
        MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0,
            fc.size());

        return Charset.defaultCharset().decode(bb).toString();
      } catch (IOException e) {
        LOGGER.error("Cannot read seedlink channel cache. ({})", infoStringFile);
      } finally {
        try {
          if (stream != null) {
            stream.close();
          }
        } catch (IOException ignore) {
          // ignore
        }
      }

    }

    return infoString;
  }


  private void writeChannelCache(String infoString) {
    if (infoStringFile == null) {
      return;
    }
    FileWriter writer = null;
    try {
      writer = new FileWriter(infoStringFile);
      writer.write(infoString);
    } catch (IOException e) {
      LOGGER.error("Cannot write seedlink channel cache. ({})", infoStringFile);
    } finally {
      try {
        if (writer != null) {
          writer.close();
        }
      } catch (IOException ignore) {
        // ignore
      }
    }
  }

  /**
   * Get a copy of this data source.
   * 
   * @return a copy of this data source.
   */
  public SeismicDataSource getCopy() {
    return new SeedLinkSource(name, params);
  }

  /**
   * Get the gulper key for the specified station.
   * 
   * @param station the station.
   * @return the gulper key.
   */
  private String getGulperKey(String station) {
    return DataSourceType.getShortName(SeedLinkSource.class) + ":" + station;
  }

  /**
   * Get the helicorder data.
   * 
   * @param scnl the scnl.
   * @param t1 the start time.
   * @param t2 the end time.
   * @param gl the gulper listener.
   * @return the helicorder data or null if none.
   */
  public HelicorderData getHelicorder(String scnl, double t1, double t2,
      GulperListener gl) {
    // check if data is in the cache
    HelicorderData hd = CachedDataSource.getInstance().getHelicorder(scnl,
        t1, t2, gl);
    // if no data or data start time is greater than requested
    if (hd == null || hd.rows() == 0 || (hd.getStartTime() - t1 > 10)
        || hd.getEndTime() < t2) {
      requestGulper(scnl, t1, t2, gl);

    } else if (hd.getEndTime() < t2) {
      // if data end time is less than requested
      requestGulper(scnl, hd.getEndTime(), t2, gl);
    }

    LOGGER.debug("getHelicorder(scnl={}, start={}, end={})\nDATA={}", scnl, J2kSec.toDateString(t1),
        J2kSec.toDateString(t2), (hd == null ? "NONE" : hd.toString()));
    return hd;
  }


  /**
   * Either returns the wave successfully or null if the data source could not
   * get the wave.
   * 
   * @param scnl the scnl.
   * @param t1 the start time.
   * @param t2 the end time.
   * @return the wave or null if none.
   */
  public Wave getWave(String scnl, double t1, double t2) {
    SeedLinkGulperListener gulperListener = gulperListeners.get(scnl);
    if (gulperListener != null && gulperListener.isAlive() == false) {
      gulperListeners.remove(scnl);
      gulperListener = null;
    }

    if (gulperListener == null) {
      gulperListener = new SeedLinkGulperListener();
      gulperListeners.put(scnl, gulperListener);
      Gulper gulper = GulperList.INSTANCE.requestGulper(getGulperKey(scnl), gulperListener,
          this.getCopy(), scnl, t1, t2, 0, 1000);
      gulperListener.setGulper(gulper);
    }

    SeedLinkGulperListener gulperListner = gulperListeners.get(scnl);
    gulperListner.read();

    return CachedDataSource.getInstance().getBestWave(scnl, t1, t2);

    // // check if data is in the cache
    // Wave wave = CachedDataSource.getInstance().getWave(scnl, t1, t2);
    // if (wave == null) {
    // // remove all data in the future to avoid blocking
    // final double now = J2kSec.now();
    // if (t1 <= now) {
    // final SeedLinkClient client = createClient();
    // wave = client.getWave(scnl, t1, t2);
    // removeClient(client);
    // }
    // }
    //
    // LOGGER.debug("getWave(scnl={}, start={}, end={})\nDATA={}", scnl, J2kSec.toDateString(t1),
    // J2kSec.toDateString(t2), (wave == null ? "NONE" : wave.toString()));
    // return wave;
  }

  /**
   * Is this data source active; that is, is new data being added in real-time
   * to this data source?
   * 
   * @return whether or not this is an active data source.
   */
  public boolean isActiveSource() {
    return true;
  }

  public synchronized void notifyDataNotNeeded(String station, double t1,
      double t2, GulperListener gl) {
    GulperList.INSTANCE.killGulper(getGulperKey(station), gl);
  }


  /**
   * Create a client.
   * 
   * @return the client.
   */
  protected SeedLinkClient createClient() {
    final SeedLinkClient client = new SeedLinkClient(host, port);
    synchronized (seedLinkClientList) {
      seedLinkClientList.add(client);
    }
    return client;
  }


  /**
   * Remove the client.
   * 
   * @param client the client.
   */
  protected void removeClient(SeedLinkClient client) {
    synchronized (seedLinkClientList) {
      seedLinkClientList.remove(client);
    }
  }


  /**
   * Request data from the gulper.
   * 
   * @param scnl the scnl.
   * @param t1 the start time.
   * @param t2 the end time.
   * @param gl the gulper listener.
   */
  protected void requestGulper(String scnl, double t1, double t2,
      GulperListener gl) {
    GulperList.INSTANCE.requestGulper(getGulperKey(scnl), gl, this,
        scnl, t1, t2, GULP_SIZE, GULP_DELAY);
  }

  /**
   * Get the configuration string.
   *    
   * @return the configuration string.
   */
  public String toConfigString() {
    return String.format("%s;%s:%s:%d", name, DataSourceType.getShortName(SeedLinkSource.class),
        host, port);
  }

}
