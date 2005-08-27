package gov.usgs.swarm;
 
import gov.usgs.swarm.data.CachedDataSource;
import gov.usgs.swarm.data.SeismicDataSource;
import gov.usgs.util.ConfigFile;
import gov.usgs.util.CurrentTime;
import gov.usgs.util.ui.GlobalKeyManager;
import gov.usgs.vdx.data.wave.Wave;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.DefaultDesktopManager;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;

/**
 * Main application class.
 *
 * $Log: not supported by cvs2svn $
 * Revision 1.12  2005/05/02 16:22:11  cervelli
 * Moved data classes to separate package.
 *
 * Revision 1.11  2005/04/27 03:52:10  cervelli
 * Peter's configuration changes.
 *
 * Revision 1.10  2005/04/25 22:45:32  cervelli
 * 1.1.12 version bump.
 *
 * Revision 1.9  2005/04/11 00:26:11  cervelli
 * Don't use the stupid JDK 1.5 Swing theme.
 *
 * Revision 1.8  2005/03/28 17:11:20  cervelli
 * Final 1.1.10 version bump.
 *
 * Revision 1.7  2005/03/26 17:29:57  cervelli
 * "--sleep" option.
 *
 * Revision 1.6  2005/03/25 00:49:23  cervelli
 * Initial version to support WWS.
 *
 * Revision 1.5  2005/03/24 20:50:08  cervelli
 * User specified group config file; tile 4 helicorders to quadrants.
 *
 * Revision 1.4  2004/10/28 20:16:51  cvs
 * Big red mouse cursor support and version bump.
 *
 * Revision 1.3  2004/10/23 19:35:30  cvs
 * Version bump.
 *
 * Revision 1.2  2004/10/12 23:45:11  cvs
 * Bumped version, added log.
 *
 * TODO: external image names and use getResource.  Also package images in jar.
 * @author Dan Cervelli
 */
public class Swarm extends JFrame
{
	private static final long serialVersionUID = -1;
	private static String DEFAULT_CONFIG_FILE = "Swarm.config";
	private static Swarm parentFrame;
	private ConfigFile config;
	private JDesktopPane desktop;
	private JSplitPane split;
	private ChannelPanel channelPanel;
	private DataSourceChooser chooser;
	private JMenuBar menuBar;
	private CachedDataSource cache;
	private AboutDialog aboutDialog;
	private JLabel threadLabel;
	private JPanel leftPanel;
	private int frameCount = 0;
	private int threadCount = 0;
	
	private WaveClipboardFrame waveClipboard;
	
	private static final String TITLE = "Swarm";
	private static final String VERSION = "1.2.0-a20050826";
	
	private List<JInternalFrame> frames;
	private boolean fullScreen = false;
	private int oldState = 0;
	private Dimension oldSize;
	private Point oldLocation;
	private JFileChooser fileChooser;

	private Map<String, MultiMonitor> monitors;
	
//	private String groupConfigFile;
	
	private AbstractAction toggleFullScreenAction;	
					
	public Swarm(String[] args)
	{
		super(TITLE + " [" + VERSION + "]");
		
		String version = System.getProperty("java.version");
		if (version.startsWith("1.1") || version.startsWith("1.2") || version.startsWith("1.3"))
		{
			JOptionPane.showMessageDialog(this, "Swarm requires at least Java version 1.4 or above.", "Error",
					JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		}

		loadFileChooser();
		monitors = new HashMap<String, MultiMonitor>();
		
		// clean this up a bit and decide if I really want to use this ghkm thingy
		GlobalKeyManager m = GlobalKeyManager.getInstance();
		m.getInputMap().put(KeyStroke.getKeyStroke("F12"), "focus");
		m.getActionMap().put("focus", new AbstractAction()
				{
					private static final long serialVersionUID = -1;
					public void actionPerformed(ActionEvent e)
					{
						KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
						System.out.println("Focus check: \n" + 
								"Current window: " + kfm.getFocusedWindow() + "\n\n" +
								"Current component: " + kfm.getFocusOwner() + "\n");	
					}
				});
				
		m.getInputMap().put(KeyStroke.getKeyStroke("alt F12"), "outputcache");
		m.getActionMap().put("outputcache", new AbstractAction()
				{
					private static final long serialVersionUID = -1;
					public void actionPerformed(ActionEvent e)
					{
						if (cache != null)
							cache.output();
					}
				});
				
		m.getInputMap().put(KeyStroke.getKeyStroke("control F12"), "flushcache");
		m.getActionMap().put("flushcache", new AbstractAction()
				{
					private static final long serialVersionUID = -1;
					public void actionPerformed(ActionEvent e)
					{
						if (cache != null)
							cache.flush();
					}
				});

		m.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0), "fullScreenToggle");
		m.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F11, InputEvent.CTRL_DOWN_MASK), "fullScreenToggle");
		m.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SLASH, InputEvent.CTRL_DOWN_MASK), "fullScreenToggle");
		toggleFullScreenAction = new AbstractAction()
		{
			private static final long serialVersionUID = -1;
		
			public void actionPerformed(ActionEvent e)
			{
				toggleFullScreenMode();					
				Swarm.this.requestFocus();
			}	
		};
		m.getActionMap().put("fullScreenToggle", toggleFullScreenAction);	
  
		String configFile = DEFAULT_CONFIG_FILE;
		  
		int n = args.length - 1;
		if (n >= 0 && !args[n].startsWith("-"))
			configFile = args[n];
		  
		parseConfigFile(configFile);
		config.put("configFile", configFile, false);
		   
		for (int i = 0; i <= n; i++)
		{
			if (args[i].startsWith("--"))
			{
				String key = args[i].substring(2, args[i].indexOf('='));
				String val = args[i].substring(args[i].indexOf('=') + 1);
				System.out.println(key + " = " + val);
				config.put(key, val, false);
			}
		}
		 
		checkConfig();  
  
		cache = new CachedDataSource();
		
		parentFrame = this;
		frames = new ArrayList<JInternalFrame>();
		createUI();
	}
	
	private void loadFileChooser()
	{
		Thread t = new Thread(new Runnable() 
				{
					public void run()
					{
						fileChooser = new JFileChooser();
					}
				});
				
		t.setPriority(Thread.MIN_PRIORITY);
		t.start();
	}
	
	public JFileChooser getFileChooser()
	{
		int timeout = 10000;
		while (fileChooser == null && timeout > 0)
		{
			try { Thread.sleep(100); } catch (Exception e) {}
			timeout -= 100;
		}
		return fileChooser;
	}
	
	public static String getVersion()
	{
		return VERSION;
	}
	
	public static CachedDataSource getCache()
	{
		return parentFrame.cache;
	}

	public void parseConfigFile(String fn)
	{
		config = new ConfigFile(fn);
		if (config == null)
			config = new ConfigFile();
	}
	
	public void checkConfig()
	{
		if (config.get("timeZoneAbbr") == null)
			config.put("timeZoneAbbr", "UTC", false);
		
		if (config.get("timeZoneOffset") == null)
			config.put("timeZoneOffset", "0", false);
			
		if (config.get("windowX") == null)
			config.put("windowX", "50", false);
			
		if (config.get("windowY") == null)
			config.put("windowY", "50", false);
			
		if (config.get("windowSizeX") == null)
			config.put("windowSizeX", "800", false);
			
		if (config.get("windowSizeY") == null)
			config.put("windowSizeY", "600", false);
			
		if (config.get("windowMaximized") == null)
			config.put("windowMaximized", "false", false);
		
		if (config.get("useLargeCursor") == null)
			config.put("useLargeCursor", "false", false);
			
		if (config.get("span") == null)
			config.put("span", "24", false);
			
		if (config.get("timeChunk") == null)
			config.put("timeChunk", "30", false);
			
		if (config.get("lastPath") == null)
			config.put("lastPath", "default", false);
  
		if (config.get("kiosk") == null)
			config.put("kiosk", "false", false);
		  
		if (config.get("groupConfigFile") == null)
			config.put("groupConfigFile", "SwarmGroups.config", false);
		  
		if (config.get("saveConfig") == null)
			config.put("saveConfig", "true", false);
	}
	
	public WaveClipboardFrame getWaveClipboard()
	{
		return waveClipboard;	
	}
	
	public ConfigFile getConfig()
	{
		return config;	
	}
	
	public static Swarm getParentFrame()
	{
		return parentFrame;	
	}
	
	public void createUI()
	{
		this.addWindowListener(new WindowAdapter()
				{
					public void windowClosing(WindowEvent e)
					{
						closeApp();
					}
				});
		this.addFocusListener(new FocusListener()
				{
					public void focusGained(FocusEvent e)
					{
						// The main Swarm window has no need for the focus.  If it gets it 
						// then it attempts to pass it on to the first helicorder, failing
						// that it gives it to the first wave.
						if (frames != null && frames.size() > 0)
						{
							JInternalFrame jf = null;
							for (int i = 0; i < frames.size(); i++)
							{
								JInternalFrame f = (JInternalFrame)frames.get(i);
								if (f instanceof HelicorderViewerFrame)
								{
									jf = f;
									break;
								}
							}
							if (jf == null)
								jf = (JInternalFrame)frames.get(0);
							jf.requestFocus();
						}
					}
					
					public void focusLost(FocusEvent e)
					{}
				});
		leftPanel = new JPanel(new BorderLayout());
		channelPanel = new ChannelPanel(this);
		channelPanel.setMinimumSize(new Dimension(190, 150));
		chooser = new DataSourceChooser(this);
		chooser.setMinimumSize(new Dimension(190, 150));
		
		JSplitPane leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chooser, channelPanel);
		leftSplit.setOneTouchExpandable(true);
		leftPanel.add(leftSplit, BorderLayout.CENTER);
		
		desktop = new JDesktopPane();
		desktop.setDragMode(JDesktopPane.OUTLINE_DRAG_MODE);
		// disable dragging in fullscreen mode
		desktop.setDesktopManager(new DefaultDesktopManager()
				{
					private static final long serialVersionUID = -1;
					public void beginDraggingFrame(JComponent f)
					{
						if (fullScreen)
							return;
						else
							super.beginDraggingFrame(f);
					}
					
					public void dragFrame(JComponent f, int x, int y)
					{
						if (fullScreen)
							return;
						else
							super.dragFrame(f, x, y);
					}
				});
		this.setSize(Integer.parseInt(config.getString("windowSizeX")), Integer.parseInt(config.getString("windowSizeY")));
		this.setLocation(Integer.parseInt(config.getString("windowX")), Integer.parseInt(config.getString("windowY")));
		if (config.getString("windowMaximized").equals("true"))
			this.setExtendedState(Frame.MAXIMIZED_BOTH);
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, desktop);
		split.setOneTouchExpandable(true);
		split.setDividerLocation(200);
		this.setContentPane(split);	
		
		menuBar = new JMenuBar();
		JMenu fileMenu = new JMenu("File");
		JMenuItem exit = new JMenuItem("Exit");
		exit.addActionListener(new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						closeApp();
					}
				});
		fileMenu.add(exit);
		JMenu editMenu = new JMenu("Edit");
		JMenuItem options = new JMenuItem("Options...");
		options.addActionListener(new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						OptionsDialog od = new OptionsDialog();
						od.setVisible(true);
					}
				});
		editMenu.add(options);
		JMenu windowMenu = new JMenu("Window");
		JMenuItem tileHelis = new JMenuItem("Tile Helicorders");
		tileHelis.addActionListener(new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						tileHelicorders();
					}
				});
		windowMenu.add(tileHelis);
		JMenuItem tileWaves = new JMenuItem("Tile Waves");
		tileWaves.addActionListener(new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						tileWaves();
					}
				});
		windowMenu.add(tileWaves);
		windowMenu.addSeparator();
		JMenuItem fullScreenItem = new JMenuItem("Kiosk Mode");
		fullScreenItem.addActionListener(toggleFullScreenAction);
		fullScreenItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0));
		windowMenu.add(fullScreenItem);
		JMenu helpMenu = new JMenu("Help");
		JMenuItem about = new JMenuItem("About...");
		aboutDialog = new AboutDialog();
		about.addActionListener(new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						aboutDialog.update();
						aboutDialog.setVisible(true);
					}
				});
				
		helpMenu.add(about);
		menuBar.add(fileMenu);
		menuBar.add(editMenu);
		menuBar.add(windowMenu);
		menuBar.add(helpMenu);
		menuBar.add(Box.createHorizontalGlue());
		threadLabel = new JLabel(" ");
		menuBar.add(threadLabel);
		this.setJMenuBar(menuBar);
		
		waveClipboard = new WaveClipboardFrame();
		desktop.add(waveClipboard);
		
		this.setVisible(true);
		long offset = CurrentTime.getOffset();
		if (Math.abs(offset) > 10 * 60 * 1000)
			JOptionPane.showMessageDialog(this, "You're system clock is off by more than 10 minutes.\n" + 
					"This is just for your information, Swarm will not be affected by this.", "System Clock", JOptionPane.INFORMATION_MESSAGE);
	}
	
	public boolean isFullScreenMode()
	{
		return fullScreen;	
	}
	
	public void toggleFullScreenMode()
	{
		fullScreen = !fullScreen;
		setFullScreenMode(fullScreen);
	}
	
	private void setFullScreenMode(boolean full)
	{
		this.dispose();
		this.setUndecorated(full);
		this.setResizable(!full);
		waveClipboard.setVisible(!full);
		waveClipboard.toBack();
		if (full)
		{
			this.setJMenuBar(null);
			oldState = this.getExtendedState();
			oldSize = this.getSize();
			oldLocation = this.getLocation();
			
			this.setContentPane(desktop);
			// having two setVisibles makes the desktop resize it self appropriately
			this.setVisible(true);
			this.setExtendedState(Frame.MAXIMIZED_BOTH);
			desktop.setSize(this.getSize());
			desktop.setPreferredSize(this.getSize());
			desktop.setMinimumSize(this.getSize());
			desktop.setMaximumSize(this.getSize());
//			this.setVisible(false);
		}
		else
		{
			this.setJMenuBar(menuBar);
			this.setExtendedState(oldState);
			this.setSize(oldSize);
			this.setLocation(oldLocation);
			split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, desktop);
			split.setOneTouchExpandable(true);
			split.setDividerLocation(200);
			this.setContentPane(split);
		}
		validate();
		this.setVisible(true);
		//for (int i = 0; i < frames.size(); i++)
		for (JInternalFrame frame : frames)
		{
//			if (frames.elementAt(i) instanceof HelicorderViewerFrame)
			if (frame instanceof HelicorderViewerFrame)
			{
				HelicorderViewerFrame f = (HelicorderViewerFrame)frame; 
				f.setFullScreen(full);
			}
		}
		tileHelicorders();
	}
	
	public void closeApp()
	{
		Point p = this.getLocation();

		if (this.getExtendedState() == Frame.MAXIMIZED_BOTH)
			config.put("windowMaximized", "true", false);
		else
		{
			Dimension d = this.getSize();
			config.put("windowX", Integer.toString(p.x), false);
			config.put("windowY", Integer.toString(p.y), false);
			config.put("windowSizeX", Integer.toString(d.width), false);
			config.put("windowSizeY", Integer.toString(d.height), false);
			config.put("windowMaximized", "false", false);
		}
  
		if ((config.getString("saveConfig")).equals("true"))
		{
			String configFile = config.getString("configFile");
			config.remove("configFile");
			config.writeToFile(configFile);
		}
  
		waveClipboard.removeWaves();
		try
		{
//			for (int i = 0; i < frames.size(); i++)
//				((JInternalFrame)frames.elementAt(i)).setClosed(true);
			for (JInternalFrame frame : frames)
				frame.setClosed(true);
		}
		catch (Exception e) {} // doesn't matter at this point
		System.exit(0);
	}
	
	public String findSource(String abbr)
	{
		java.util.List sl = config.getList("server");
		Iterator it = sl.iterator();
		while (it.hasNext())
		{
			String s = (String)it.next();
			if (s.startsWith(abbr))
				return s;
		}
		return null;
	}
	
	public boolean sourceExists(String abbr)
	{
		return findSource(abbr) != null;
	}
	
	public SeismicDataSource parseDataSource(String abbrSource)
	{
		String source = findSource(abbrSource);
		return SeismicDataSource.getDataSource(source);
	}
	
	public void dataSourceSelected(final String source)
	{
		final SwingWorker worker = new SwingWorker()
				{
					private java.util.List ws;
					private java.util.List hs;
					private boolean failed;
					
					public Object construct()
					{
						incThreadCount();
						try
						{
							SeismicDataSource sds = parseDataSource(source);
							if (sds != null)
							{
								ws = sds.getWaveStations();
								hs = sds.getHelicorderStations();
								sds.close();
							}
							else 
								failed = true;
						} 
						catch (Exception e)
						{
							failed = true;
						}
						return null;	
					}			
					
					public void finished()
					{
						if (!failed)
						{
							channelPanel.populateWaveChannels(source, ws);
							channelPanel.populateHelicorderChannels(source, hs);
						}
						chooser.enableGoButton();
						decThreadCount();
					}
				};
		worker.start();
	}

	public void clipboardWaveChannelSelected(final String source, final String[] channels)
	{
		final SeismicDataSource sds = parseDataSource(source);
		for (int i = 0; i < channels.length; i++)
			loadClipboardWave(sds, channels[i]);
	}
	
	public void clipboardWaveChannelSelected(final String source, final String channel)
	{
		final SeismicDataSource sds = parseDataSource(source);
		loadClipboardWave(sds, channel);
	}
	
	private void loadClipboardWave(final SeismicDataSource source, final String channel)
	{
		final WaveViewPanel wvp = new WaveViewPanel();
		wvp.setChannel(channel);
		wvp.setDataSource(source);
		ClipboardWaveViewPanel cwvp = waveClipboard.getSelected();
		double st = 0;
		double et = 0;
		if (cwvp == null)
		{
			double now = CurrentTime.nowJ2K();
			st = now - 180;
			et = now;
		}
		else
		{
			st = cwvp.getWaveViewPanel().getStartTime();	
			et = cwvp.getWaveViewPanel().getEndTime();
		}
		final double fst = st;
		final double fet = et;
		
		final SwingWorker worker = new SwingWorker()
				{
					public Object construct()
					{
						incThreadCount();
//						double now = CurrentTime.nowJ2K();
						Wave sw = source.getWave(channel, fst, fet);
						wvp.setWave(sw, fst, fet);
						return null;
					}
					
					public void finished()
					{
						waveClipboard.toFront();
						try
						{
							waveClipboard.setSelected(true);
						}
						catch (Exception e) {}
						waveClipboard.addWave(new ClipboardWaveViewPanel(wvp));
						decThreadCount();
					}
				};
		worker.start();
	}
	
	/*
	public NewWaveViewerFrame waveChannelSelected(String source, String channel)
	{
		SeismicDataSource sds = parseDataSource(source);
		NewWaveViewerFrame frame = new NewWaveViewerFrame(this, sds, channel);
		addInternalFrame(frame);
		return frame;
	}
	*/

	public void monitorChannelSelected(String source, String[] channels)
	{
		MultiMonitor monitor = (MultiMonitor)monitors.get(source);
		if (monitor == null)
		{
			SeismicDataSource sds = parseDataSource(source);
			monitor = new MultiMonitor(sds);
			monitors.put(source, monitor);
			addInternalFrame(monitor);
		}
	
		if (!monitor.isVisible())
			monitor.setVisible(true);
		for (int i = 0; i < channels.length; i++)
			monitor.addChannel(channels[i]);
	}
	
	public WaveViewerFrame waveChannelSelected(String source, String channel)
	{
		SeismicDataSource sds = parseDataSource(source);
		WaveViewerFrame frame = new WaveViewerFrame(this, sds, channel);
		addInternalFrame(frame);
		return frame;
	}
	
	public HelicorderViewerFrame helicorderChannelSelected(String source, String channel)
	{
		SeismicDataSource sds = parseDataSource(source);
		HelicorderViewerFrame frame = new HelicorderViewerFrame(this, sds, channel);
		addInternalFrame(frame);
		return frame;
	}
	
	public void removeInternalFrame(final JInternalFrame f)
	{
		SwingUtilities.invokeLater(new Runnable() 
				{
					public void run()
					{
						frames.remove(f);
						if (frameCount > 0)
							frameCount--;
					}
				});			
	}
	
	public void addInternalFrame(final JInternalFrame f)
	{
		frames.add(f);
		frameCount++;			
		frameCount = frameCount % 10;
		f.setLocation(frameCount * 30, frameCount * 30);
		SwingUtilities.invokeLater(new Runnable()
				{
					public void run()
					{
						desktop.add(f);
						f.toFront();
						try
						{
							f.setSelected(true);
						}
						catch (Exception e) {}
					}
				});
	}
	
	public void tileHelicorders()
	{
		Dimension ds = desktop.getSize();

		ArrayList<HelicorderViewerFrame> hcs = new ArrayList<HelicorderViewerFrame>(10);
//		for (int i = 0; i < frames.size(); i++)
		for (JInternalFrame frame : frames)
		{
			if (frame instanceof HelicorderViewerFrame)
			    hcs.add((HelicorderViewerFrame)frame);
		}
		
		if (hcs.size() == 0)
			return;
		
		if (hcs.size() == 4)
		{
		    int w = ds.width / 2;
		    int h = ds.height / 2;
		    HelicorderViewerFrame hvf0 = (HelicorderViewerFrame)hcs.get(0);
		    HelicorderViewerFrame hvf1 = (HelicorderViewerFrame)hcs.get(1);
		    HelicorderViewerFrame hvf2 = (HelicorderViewerFrame)hcs.get(2);
		    HelicorderViewerFrame hvf3 = (HelicorderViewerFrame)hcs.get(3);
		    hvf0.setSize(w, h);
		    hvf0.setLocation(0, 0);
		    hvf1.setSize(w, h);
		    hvf1.setLocation(w, 0);
		    hvf2.setSize(w, h);
		    hvf2.setLocation(0, h);
		    hvf3.setSize(w, h);
		    hvf3.setLocation(w, h);
		}
		else
		{
		    int w = ds.width / hcs.size();
			int cx = 0;
			for (int i = 0; i < hcs.size(); i++)
			{
				HelicorderViewerFrame hvf = (HelicorderViewerFrame)hcs.get(i);
				try 
				{ 
					hvf.setIcon(false);
					hvf.setMaximum(false);
				}
				catch (Exception e) {}
				hvf.setSize(w, ds.height);
				hvf.setLocation(cx, 0);
				cx += w;
			}
		}
	}
	
	public void tileWaves()
	{
		Dimension ds = desktop.getSize();
		
		int wc = 0;
//		for (int i = 0; i < frames.size(); i++)
		for (JInternalFrame frame : frames)
		{
			if (frame instanceof WaveViewerFrame)
				wc++;	
		}
		
		if (wc == 0)
			return; 
			
		int h = ds.height / wc;
		int cy = 0;
//		for (int i = 0; i < frames.size(); i++)
		for (JInternalFrame frame : frames)
		{
			if (frame instanceof WaveViewerFrame)
			{
				WaveViewerFrame wvf = (WaveViewerFrame)frame;//frames.elementAt(i);
				try 
				{ 
					wvf.setIcon(false);
					wvf.setMaximum(false);
				}
				catch (Exception e) {}
				wvf.setSize(ds.width, h);
				wvf.setLocation(0, cy);
				cy += h;
			}
		}
	}

	public void updateThreadLabel()
	{
		if (threadCount == 0)
			threadLabel.setText(" ");
		else if (threadCount == 1)
			threadLabel.setText("1 thread ");
		else
			threadLabel.setText(threadCount + " threads ");
	}

	public void incThreadCount()
	{
		threadCount++;	
		updateThreadLabel();
	}
	
	public void decThreadCount()
	{
		threadCount--;	
		updateThreadLabel();
	}
	
	public void parseKiosk()
	{
//		String[] kiosks = Util.splitString(getConfig().getString("kiosk"), ",");
		String[] kiosks = getConfig().getString("kiosk").split(",");
		for (int i = 0; i < kiosks.length; i++)
		{ 
//			String[] ch = Util.splitString(kiosks[i], ";");
			String[] ch = kiosks[i].split(";");
//			HelicorderViewerFrame f = helicorderChannelSelected(ch[0], ch[1]);
			helicorderChannelSelected(ch[0], ch[1]);
		}
		toggleFullScreenMode();
	}
	
	public void optionsChanged()
	{
//		for (int i = 0; i < frames.size(); i++)
		for (JInternalFrame frame : frames)
		{
			if (frame instanceof HelicorderViewerFrame)
			{
				HelicorderViewerFrame hvf = (HelicorderViewerFrame)frame;
				hvf.getHelicorderViewPanel().cursorChanged();
			}
		}
	}
	
	public static void main(String[] args)
	{
		try 
		{
//			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			// JDK 1.5 by default has an ugly theme, this line uses the one from 1.4
			MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme());
			//UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
		}
		catch (Exception e) { }

		Swarm swarm = new Swarm(args);
		if (!(Swarm.getParentFrame().getConfig().getString("kiosk")).equals("false"))
			swarm.parseKiosk();
	}
}