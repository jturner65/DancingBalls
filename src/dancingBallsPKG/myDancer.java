package dancingBallsPKG;

import java.util.*;
import java.util.concurrent.*;

//base class that holds common code and method prototypes for objects intended to respond to audio excitation ("dance")
public abstract class myDancer {
	public DancingBalls pa;
	public DancingBallWin win;
	public String name;
	public static int baseID = 0;
	public int ID;
	//spring to rest position for all particles
	public mySpringToRest springForce;
	//constants - manage via UI?
	public final double ksMod4kp = .8;
	//making spring constant derived from a multiple of 120 bpm beat frequency (->ks==158, 79) 
	//caused weird constructive/destructive interference in motion.
	public double sprKs = 50.0,//
			sprKd = ksMod4kp*sprKs;

	protected static final float fourPiSq = (float) (4*Math.PI*Math.PI);

	
	public boolean[] flags;
	public static final int 
		debugIDX 		= 0,		//debug mode
		isPtsMadeIDX 	= 1,		//are points describing dancer made?
		isZoneMappedIDX = 2,		//are zones mapped on dancer?
		isInitedIDX	    = 3,		//all all initialization procedures complete?
		audioValsSetIDX	= 4,		//if true, audio values from fft are set
		dispVertHiLiteIDX = 5;		//display highlights in verts to show something has happened
	public static final int numFlags = 6;
	
	
	
	public myDancer(DancingBalls _p, DancingBallWin _win, String _name) {
		pa = _p; win = _win; name = _name;	ID = baseID++;
		initMe();
	}//ctor
	
	public void initMe() {
		initFlags();
		//springs should be built before zone points
		springForce = new mySpringToRest(pa, "Spring To Rest", sprKs, sprKd);			
		initMePriv();//implementation-specific init before child class ctor is called
	}
	protected abstract void initMePriv();
	//init called at end of implementing class's ctor
	protected abstract void beginInit();
	//called at end of initialization
	protected abstract void finalInit();
	
	protected void initFlags() {flags = new boolean[numFlags];for(int i=0;i<numFlags;++i) {flags[i]=false;}}
	public void setFlags(int idx, boolean val) {
		flags[idx] = val;
		switch (idx) {
		case debugIDX 			:{break;}
		case isPtsMadeIDX 		:{break;}
		case isZoneMappedIDX 	:{break;}
		case isInitedIDX	    :{break;}
		case audioValsSetIDX	:{break;}
		}
	}//setFlags
	
	
	
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

class myDancingSnowman extends myDancer {	
	public String[] objNames = new String[]{"Head","Torso","Butt","Left Hand","Right Hand"};
	
	public ConcurrentSkipListMap<String, myDancingBall> snowman;
	

	public myDancingSnowman(DancingBalls _p, DancingBallWin _win, String _name) {
		super(_p,_win,_name);
		snowman = new ConcurrentSkipListMap<String, myDancingBall>();
		beginInit();
	}
	
	@Override
	protected void initMePriv() {}	

	@Override
	protected void beginInit() {		
	}

	//initialize components necessary once mapping is finished from multi-threaded builder
	@Override
	protected void finalInit() {		
	}



	//draw each individual snowman component
	public void drawMe() {
		
	}
	@Override
	public void rebuildMe() {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void drawMe(float timer) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void setSimVals() {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void simMe(float modAmtMillis, boolean stimTaps, boolean useFrc) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void setZoneModStimType(int stimType) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void setFreqVals(float[] _bandRes) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void debug0() {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void debug1() {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void debug2() {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void debug3() {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void resetConfig() {
		// TODO Auto-generated method stub
		
	}
	@Override
	public int getZoneSize(int zoneToShow) {
		// TODO Auto-generated method stub
		return 0;
	}

	
}//class myDancingSnowmanf



//class to hold a zone focal point
class myZonePoint{
	public static DancingBalls pa;
	public static myDancer dancer;
	//spring for force-based calculations
	public mySpringToRest springForce;

	public myVectorf pt, drawPt;
	public int zoneIDX, zoneID;
	public float zoneMrkrSz;
	//opposite mate - to use if we want to excite opposites in phase or 180 out of phase
	public myZonePoint mPt;
	public boolean mateSet = false;
	//closest and furthest distance to this zone point, to be used to scale displacement input
	public float minDist, maxDist, interpDenom;
	
	//map holding distances and verts of neighborhood to this zone point
	private ConcurrentNavigableMap<Float,myRndrdPart> ngbhd;
	
	public myZonePoint(DancingBalls _pa, myDancer _dancer, int _zIdx, int _zID, myVectorf _pt){
		pa = _pa;dancer=_dancer;pt = _pt;zoneIDX = _zIdx; zoneID = _zID;
		//TODO change to per-zone spring
		//springForce = _ball.springsPerZone[zoneIDX];		
		springForce = _dancer.springForce;		
		zoneMrkrSz = pa.max(1.0f,10.0f - 2*zoneIDX); 
		drawPt = new myVectorf(pt); drawPt._mult(2.0f);		
	}
	//sets this point and passed mate's point to be mated
	public void setMate(myZonePoint _mate) {mPt = _mate; _mate.mPt = this;mateSet=true; _mate.mateSet = true;}
	
	public void setNeighborhood(ConcurrentNavigableMap<Float,myRndrdPart> _ngbhd) {
		ngbhd = _ngbhd;
		minDist = ngbhd.firstKey();
		maxDist = ngbhd.lastKey();	
		interpDenom = 1.0f/(maxDist - minDist);
	}

	//stimulate zone with stim value as a displacement, scaled for distance from zone point
	public void stimulateZoneKin(float stim) {
		for(Float key : ngbhd.keySet()) {
			float stimVal = stim * (1 - ((key - minDist)*interpDenom));
			//System.out.println("for initial stim : " + stim + " and for dist : " + key + " stim val is " + stimVal);
			ngbhd.get(key).displace(stimVal);
		}	
	}//stimulateZone
	//stimulate a zone with a force, scaled for distance from zone point - add force in direction of normal
	public void stimulateZoneForce(float stim) {//
		for(Float key : ngbhd.keySet()) {
			float stimVal = stim * (1 - ((key - minDist)*interpDenom));
			//System.out.println("for initial stim : " + stim + " and for dist : " + key + " stim val is " + stimVal);
			myRndrdPart part =ngbhd.get(key); 
			part.stimulateFrc(stimVal);
		}			
	}//stimulateZoneForce	
	//stimulate a zone with a force, account for this zone's spring, scaled for distance from zone point - add force in direction of normal
	public void stimulateZoneForceWithSpring(float stim) {//
		for(Float key : ngbhd.keySet()) {
			float stimVal = stim * (1 - ((key - minDist)*interpDenom));
			//System.out.println("for initial stim : " + stim + " and for dist : " + key + " stim val is " + stimVal);
			myRndrdPart part =ngbhd.get(key); 
			part.stimulateFrc(stimVal);
		}
		fwdSimZoneNoStim();
	}//stimulateZoneForce	
	//for dynamic simulation, apply spring forces to rest positions and integrate
	public void fwdSimZoneNoStim() {
		myVectorf[] result;	
		for (myRndrdPart part : ngbhd.values()) {				
			result = springForce.calcForceOnParticle(part, null, 0);
			float dotProd = result[0]._dot(part.norm);
			part.stimulateFrc(dotProd);			
		}			
		for (myRndrdPart part : ngbhd.values()) {	part.integAndAdvance(dancer.win.deltaT);	}
		
	}
	
	public void resetZone() {
		for(myRndrdPart part : ngbhd.values()) {		part.reset();	}
	}
	
	public void drawMe() {
		pa.pushMatrix();pa.pushStyle();	
		if(mateSet) {
			pa.fill(255);
			pa.stroke(255);
		} else {
			pa.fill(255,0,0,255);
			pa.stroke(255,0,0,255);			
		}
		pa.translate(drawPt.x, drawPt.y, drawPt.z);
		pa.sphere(zoneMrkrSz);
		pa.rotate(pa.HALF_PI, -1, 0, 0);
		pa.text(""+zoneID, 10, 10);
		pa.popStyle();pa.popMatrix();		
	}

	public void drawMeMates() {
		pa.pushMatrix();pa.pushStyle();				
		pa.fill(255);
		pa.stroke(255,255,255,50);
		pa.strokeWeight(1.0f);
		pa.line(mPt.drawPt.x, mPt.drawPt.y, mPt.drawPt.z, drawPt.x, drawPt.y, drawPt.z);
		pa.popStyle();pa.popMatrix();		
	}
	public void drawNeighborhood(int clr) {for (myRndrdPart vert : ngbhd.values()) {vert.drawMeWithColor(clr % 22);}}
	
	//display this zone's zone point, zone idx and zone id
	public String debugMeToStr() {
		return "Zone IDX : "+zoneIDX+" Zone ID : "+ zoneID + " ZPt Loc : " + pt.toStrBrf();
	}
	
}//myZonePoint


