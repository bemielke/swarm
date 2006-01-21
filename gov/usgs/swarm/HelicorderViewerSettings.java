package gov.usgs.swarm;

/**
 * Settings for a helicorder.
 * 
 * TODO: eliminate this class in favor of vdx.HelicorderSettings
 * 
 * $Log: not supported by cvs2svn $
 * Revision 1.3  2005/10/26 16:47:38  cervelli
 * Made showClip variable configurable.  Changed manually slightly.
 *
 * Revision 1.2  2005/08/30 18:01:39  tparker
 * Add Autoscale Slider to Helicorder Viewer Frame
 *
 * Revision 1.1  2005/08/26 20:40:28  dcervelli
 * Initial avosouth commit.
 *
 * Revision 1.2  2005/03/24 20:39:25  cervelli
 * Gets default timeChunk and span from config file.
 *
 * @author Dan Cervelli
 */
public class HelicorderViewerSettings
{
	public int timeChunk; // seconds
	public int span; // minutes
	public int waveZoomOffset; // seconds
	private double bottomTime; // Double.NaN for now
	public int refreshInterval;
	public int scrollSize;
	public boolean forceCenter;
	public int clipBars;
	private long lastBottomTimeSet;
	public boolean showWiggler;
	
	public boolean autoScale;
	public boolean showClip;
	public boolean alertClip;
	public int alertClipTimeout;
	public int clipValue;
	public int barRange;
	public double barMult;
	
	public HelicorderViewerSettings()
	{
		timeChunk = Integer.parseInt(Swarm.getParentFrame().getConfig().getString("timeChunk")) * 60;
		span = Integer.parseInt(Swarm.getParentFrame().getConfig().getString("span")) * 60;
		waveZoomOffset = 30;
		bottomTime = Double.NaN;
		refreshInterval = 15;
		scrollSize = 1;
		forceCenter = false;
		clipBars = 21;
		showWiggler = false;
		
		clipValue = 2999;
		showClip = Boolean.parseBoolean(Swarm.getParentFrame().getConfig().getString("showClip"));
		alertClip = Boolean.parseBoolean(Swarm.getParentFrame().getConfig().getString("alertClip"));
		String s = Swarm.getParentFrame().getConfig().getString("alertClipTimeout");
		if (s != null)
			alertClipTimeout = Integer.parseInt(s) * 60;
		barRange = 1500;
		barMult = 3;
		autoScale = true;
	}
	
	public HelicorderViewPanel view;

	public long getLastBottomTimeSet()
	{
		return System.currentTimeMillis() - lastBottomTimeSet;
	}
	
	public void setBottomTime(double bt)
	{
		lastBottomTimeSet = System.currentTimeMillis();
		bottomTime = bt;
	}
	
	public double getBottomTime()
	{
		return bottomTime;
	}
	
	public void parseSettingsString(String o)
	{
//		String[] opts = Util.splitString(o, ",");
		String[] opts = o.split(",");
		for (int i = 0; i < opts.length; i++)
		{
			try
			{
				String key = opts[i].substring(0, opts[i].indexOf('='));
				String value = opts[i].substring(opts[i].indexOf('=') + 1);
				
				if (key.equals("x")) // minutes
					timeChunk = Integer.parseInt(value) * 60;
				else if (key.equals("y")) // hours
					span =  Integer.parseInt(value) * 60;
			}
			catch (Exception e)
			{
				System.err.println("Could not parse setting: " + opts[i]);
			}
		}
	}
	
	public void notifyView()
	{
		if (view != null)
			view.settingsChanged();	
	}
}