package dancingBallsPKG;

//class that holds common code and method prototypes for objects intended to respond to audio excitation ("dance")
public abstract class myDancer {
	public DancingBalls pa;
	public DancingBallWin win;
	public String name;
	public static int baseID = 0;
	public int ID;
	
	
	public myDancer(DancingBalls _p, DancingBallWin _win, String _name) {
		pa = _p; win = _win; name = _name;	ID = baseID++;
		
	}//ctor
	
	public abstract void rebuildMe();
	
	public abstract void drawMe(float timer);
	
	public abstract void setSimVals();
	public abstract void simMe(float modAmtMillis, boolean stimTaps, boolean useFrc);
	public abstract void setZoneModStimType(int stimType);
	public abstract void setFreqVals(float[] _bandRes);
	
	public abstract void debug0();
	public abstract void debug1();
	public abstract void debug2();
	public abstract void debug3();
	
	public abstract void resetConfig();
	
	public abstract int getZoneSize(int zoneToShow);
	
}// abstract class myDancer 
