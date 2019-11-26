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

class myDancingBall extends myDancer {
	
	//integer rep of ball color - modify this to reflect response to music
	public int ballClr = DancingBalls.gui_White;
	//at rest radius, mass of center(multiple of each vert's mass)
	public float rad, ctrMass = 10.0f;
	
	//list of vertices of dancing ball
	public myRndrdPart[] verts;
	public myRndrdPart ctrPart;
	public myForce[] dfltForces; 
	
	//spring to rest position for all particles
	//public mySpringToRest springForce;
	//public mySpringToRest[] springsPerZone;
	
	//spring ks and kd vals for each zone
	public double[][] zoneKsKdVals;
	
	//freq analysis data
	public float[] bandVals;	
	
	//UI selected zone and zone member to show being displaced - for debugging
	public int zoneTypIDX, zoneMmbrToShow;
	//current and last frame per-zone beat detection results
	public boolean[] beatDetRes;
	public boolean[] lastBeatDetRes;
	
	//neighborhoods for each of n "zones" of ball surface to displace from musical content - corresponds to # of frequency spans to respond to
	public static final int numZones = DancingBallWin.numZones;//set in owning window
	//set n to be 6 - 6 spans of frequencies in the music, to correspond to 6 different zone maps of sphere surface
	
	//array of per zone # to zone focal points for each zone - build 1 time
	public final myZonePoint[][] zonePoints = new myZonePoint[numZones][];
	//precalced random perturbation to use when calculating distances, to minimize collisions 
	public static final float[] zonePtsDel = new float[1000];
	//array of number of zone focus verts for each zone - few focus points mean large neighborhood of verts in zone 
	//low freq to high freq
	public int[] numZoneVerts = new int[] {8,16,32,64,128,256};
	
	//all zone variables predicated on 6 zones being constructed along the parameters of numZoneVerts above
	//stimulate zones based on zone location - this is per zone array of zone IDs to stimulate
	public int[][] zonesToStim = new int[][] {
		{6,7},										//lowest freq - alt between 2 : altGroupZoneMod with group size 1	
		
		{13,8,9,12,11,10},							//low bass freq - oscillate/wave through 1st half and 2nd half of array simultaneously, from back to front (in idx order) : altGroupZoneMod			

		{14,15,16,17,18,13,12,11,20,19},			//low melody - pair opposite zones front to back, and then back to front : cycleGroupZoneMod
		
		{12,13,14,15,16,17,18,19,20,21,22,23,24},	//high melody - cycle through entire set in order, and then back again descending		: cycleGroupZoneMod
		
		{15,14,13,16,17,18,22,21,20,23,24,25},		//low range high freq - stimulate 4 groups simultaneously in order (ignore 12 and 19 to make even)	: cycleGroupZoneMod
		
		{0,4,5,7,11,9,10,							//high range high freq - stimulate 3 groups simultaneously, randomly change group : randMultiGroupZoneMod
		1,14,15,18,26,21,23,
		2,13,16,19,25,22,24}
		};
	
	//object that governs zone modification
	public myZoneMod[] zoneMods;
	//per-zone count of beats before zone modification cycles to next zone member
	public final int[] zmBeatCounts = new int[] {2,4,2,2,2,2};
	//per zone # of groupings for zone mod handling - each group member at a particular idx is stimulated at the same time
	public final int[] zmGroupCount = new int[] {1,2,2,1,4,3};
	//per zone type of zone increment // - 0 : increase/wrap, 1 : increase/decrease, 2 : random
	public final int[] zmIncrType = new int[] {0,0,1,1,1,2};
	
	public float scaleZVal;//change to make an ellipsoid along z axis - set in init/	
	
	//thread runner for vertex mapper
	private myDanceBallBuilder vertMapper;
	
	
	public myDancingBall(DancingBalls _p, DancingBallWin _win, String _name, myVectorf _ctrStart,float _rad, float _scaleZVal) {
		super(_p, _win, _name);		
		scaleZVal = _scaleZVal;
		ctrPart = new myRndrdPart(pa, this, _ctrStart, new myVectorf(), new myVectorf(), SolverType.RK4, new myVectorf(), ballClr, ctrMass);
		rad = _rad;
		beginInit();		
	}//myDancingBall

	@Override
	protected void initMePriv() {}
	//start initializing this ball - only called once, from ctor
	@Override
	protected void beginInit() {
		buildZonePoints();	
		//build vertMapper callable to map sphere verts to zones
		//NOTE vertMapper needs to be rebuilt if numZones should change
		vertMapper = new myDanceBallBuilder(this);
	}//beginInit
	
	//remake ball if need to change # of zones
	private void buildZonePoints() {
		for(int i=0;i<zonePtsDel.length; ++i){zonePtsDel[i] = (float) (1.0f + ThreadLocalRandom.current().nextDouble(-.001, .001));}
		zoneKsKdVals = new double[numZones][2];
		for(int i=0;i<numZones;++i) {	
			myVectorf [][] zPtsLocs = pa.getRegularSphereList(rad, numZoneVerts[i], scaleZVal);//idx 0 is norm, idx 1 is location
			myZonePoint[] tmp = new myZonePoint[zPtsLocs.length];
			for(int j=0;j<zPtsLocs.length;++j) {		tmp[j] = new myZonePoint(pa,this,i,j,zPtsLocs[j][1]);	}
			zonePoints[i] = tmp;
			zoneKsKdVals[i][0] =sprKs;
			zoneKsKdVals[i][1] =sprKd;			
		}
		//set mate to be zone point furthest from a partcular zone point -use this to couple zones			
		for(int i=0;i<numZones;++i) {	
			for(int j=0;j<zonePoints[i].length;++j) {
				if (zonePoints[i][j].mateSet) {continue;}
				float maxDist = -1;
				myZonePoint maxPt = null;
				for(int k=0;k<zonePoints[i].length;++k) {	
					if(j==k) {continue;}
					float dist = myPointf._dist(zonePoints[i][j].pt, zonePoints[i][k].pt);
					if(dist > maxDist) {maxDist = dist;	maxPt =zonePoints[i][k];}
				}
				zonePoints[i][j].setMate(maxPt);
			}
		}

		//objects which modify zones of ball
		zoneMods = new myZoneMod[numZones];
		for(int i=0;i<numZones;++i) {
			zoneMods[i] = new myZoneMod(pa, this, i, zonesToStim[i], zmBeatCounts[i], zmGroupCount[i],zmIncrType[i]);			
			pa.outStr2Scr("zoneMods idx : "+i+" # zone pts :"+zonePoints[i].length);
		}
	}//buildZonePoints
	
	//set k values for resonant frequencies for each zone, using the frequency of the beats at that zone
	//where frequency == f and sqrt(k/m) = 2pif -> k = 4*(pi*f)^2
	//zoneFreqs is array of frequencies of beats for each zone
	
	//currently doesn't work well - last zone to set a particle's reference (highest frequency) wins
	//TODO:find mechanism by which particle being modified by multiple zones can have multiple spring constants
	public void setZoneKs(float[] zoneFreqs) {
		double ks;
		for(int i=0;i<zoneFreqs.length;++i) {
			ks = (i+1)*fourPiSq*zoneFreqs[i]*zoneFreqs[i];
			zoneKsKdVals[i][0] = ks;
			zoneKsKdVals[i][1] = ksMod4kp*ks;
			//setting these directly to the values calculated caused the spring contant to get too high
//			springsPerZone[i].setConstVal1(zoneKsKdVals[i][0]);
//			springsPerZone[i].setConstVal2(zoneKsKdVals[i][1]);
		}
		//pa.outStr2Scr("idx 0 beat freq :  " + tapBeats[0].getBeatFreq());		
	}//setZoneKs
	
		
	/**
	 * build vertices and normals of vertices for this sphere - use this to rebuild sphere
	 * @param _ctrStart : where this sphere should start
	 * @param _rad : sphere radius
	 * @param numParts : number of particle verts for this sphere
	 * @param randVerts : should verts be spaced randomly or uniformly over surface of sphere
	 */
	public void rebuildMe() {buildVertsAndNorms(win.ballRadius, win.ballNumVerts, win.getPrivFlags(win.randVertsForSphere));}
	private void buildVertsAndNorms(float _rad, int numParts, boolean randVerts) {	
		rad = _rad;
		setFlags(isPtsMadeIDX,false);
		setFlags(isInitedIDX,false);
		setFlags(isZoneMappedIDX,false);		//set true in zone mapping functions in thread call
		win.setBallIsMade(false);
		//ArrayList<myRndrdPart> vertAra = new ArrayList<myRndrdPart>();
		myVectorf[][] tmpPosNorm;
		//build particles centered around origin - ctrPart's world position will determine location of ball, once ball starts to move
		if(randVerts) {
			tmpPosNorm = new myVectorf[numParts][];
			//myVectorf tmpCtr = new myVectorf(0,0,0);
			for(int i=0;i<numParts;++i) {	tmpPosNorm[i] = pa.getRandPosOnSphere(rad,scaleZVal);}//,tmpCtr);		}			
		} else { //build sphere of regularly spaced points - might be different # of points than numParts
			tmpPosNorm = pa.getRegularSphereList(rad, numParts,scaleZVal);
			numParts = tmpPosNorm.length;//might not be the requested #
		}
		verts = new myRndrdPart[numParts];
		for(int i=0;i<numParts;++i) {verts[i] = (new myRndrdPart(pa, this, tmpPosNorm[i][1], new myVectorf(), new myVectorf(), SolverType.RK4, tmpPosNorm[i][0], ballClr));}					
		System.out.println("Setting verts : " + verts.length + " for window : "  +win.name);
		setFlags(isPtsMadeIDX, true);
		//launch thread to get closest verts to zones 
		pa.th_exec.execute(vertMapper);
	}//buildVertsAndNorms
	
	//move the center particle around to the music
	//TODO
	public void modCtrParticle(myVectorf mod) {

	}//modCtrParticle

	
	//initialize components necessary once mapping is finished from multi-threaded builder
	@Override
	protected void finalInit() {
		pa.outStr2Scr("Ball Final Init called");		
		if((!flags[isZoneMappedIDX]) || (flags[isInitedIDX])) {return;}
//		//objects which modify zones of ball
//		zoneMods = new myZoneMod[numZones];
//		for(int i=0;i<numZones;++i) {
//			zoneMods[i] = new myZoneMod(pa, this, i, zonesToStim[i], zmBeatCounts[i], zmGroupCount[i],zmIncrType[i]);			
//			pa.outStr2Scr("zoneMods idx : "+i+" # zone pts :"+zonePoints[i].length);
//		}
		pa.outStr2Scr("Ball Final Init Done");		
		setFlags(isInitedIDX, true);	
		win.setBallIsMade(true);
	}//finalInit
	
	@Override
	//get size of particular zone - used in UI only, will eventually be removed TODO
	public int getZoneSize(int zoneToShow) {	return zonePoints[zoneToShow].length;}

	
	//call after all forces have been added
	private void invokeSolver() {for (int idx = 0; idx < verts.length; ++idx) {verts[idx].integAndAdvance(win.deltaT);}}
	
	//called by owning class to set variables necessary for simulation, either kinematic or mass-spring
	@Override
	public void setSimVals() {
		/**
		 * send to ball(s) all values necessary for step of simulation.  
		 * these include, if mass-spring : 
		 * 		ks vals dependent on beat freqs
		 * 		
		 * if kinematic : 
		 * 
		 * and for both types : 
		 * 		ui-selected zone(s) and zone members for each zone to display
		 * 		beat detection results and last beat detection results (previous frame)
		 */
		//need to pre-calculate per-zone beat frequencies that we want to use to excite zones
		float[] zoneFreqs = new float[numZones];
		//TODO determine per-zone spring constants by using beat frequency in each zone (evolving)
		//1.1254 as zonefreq gives ks=50 in ball
		myBeat[] beats = win.audMgr.getBeats();
		for(int i=0;i<numZones;++i) {
			float btFreq = beats[i].getBeatFreq();
			//zoneFreqs[i] = (btFreq <= 0 ? 1.1254f : btFreq);//this value results in 50 ks
			zoneFreqs[i] = (btFreq <= 0 ? 2.0f : btFreq);//this value results in 158 ks
		}//
		//set ball zone spring constants TODO use beat frequencies
		setZoneKs(zoneFreqs);
				
		//these two are debug-related
		zoneTypIDX = win.zoneToShow;
		zoneMmbrToShow = win.zoneMmbrToShow;
		beatDetRes = win.audMgr.beatDetRes;
		lastBeatDetRes = win.audMgr.lastBeatDetRes;
	}//setBallSimVals
	
	@Override
	//modAmtMillis is time passed per frame in milliseconds
	public void simMe(float modAmtMillis, boolean stimTaps, boolean useFrc) {
		if(stimTaps) {//stimulate ball with finger taps/ detected beats - intended to be debugging mechanism
			if(useFrc){		stimBallTapsMassSprng_DBG();} 
			else {			stimBallTapsKine_DBG();}//use force deformations or kinematic deformations				
		} else {//stimulate with pure audio
			if(useFrc){		stimulateBallMassSpringRand();} //use force deformations
			else {			resetConfig();stimulateBallKineRand();}//use kinematic deformations
		}		
	}//simMe	
	
	
	//modAmtMillis is time passed per frame in milliseconds
	//this simMe uses zoneMod to manage zone stimulation - only specific zones are stimulated
	//TODO merge into code, not currently used
	public void simMeNew(float modAmtMillis, boolean stimTaps, boolean useFrc) {
		if(stimTaps) {//stimulate ball with finger taps/ detected beats - intended to be debugging mechanism
			if(useFrc){		
				stimBallTapsMassSprng_DBG();
			} 
			else {			
				stimBallTapsKine_DBG();
			}//use force deformations or kinematic deformations				
		} else {//stimulate with pure audio
			for (int i=0;i<this.zoneMods.length;++i) {
				//stim value based on old value used for kinematic stim : (i+1) * 50.0f*bandVals[i]
				zoneMods[i].modZone((i+1) * 50.0f*bandVals[i],beatDetRes[i], lastBeatDetRes[i]);
			}
		}		
	}//simMe	
	/////////
	// functions to stimulate zoneMod objects
//	/**
//	 * set a single zone mod object to use a particular stimulation type
//	 * @param zIdx zone index
//	 * @param stimType type of stim : 0 == kinematic; 1 == dynamic(mass-spring); 2 == jump(init kinematic, dynamic for rest of stim)
//	 */
//	private void setOneZoneModStimType(int zIdx, int stimType) {
//		zoneMods[zIdx].setCurZoneStim(stimType);
//	}//setOneZoneModStimType
	
	/**
	 * set a all zone mod objects to use a particular stimulation type
	 * @param stimType type of stim : 0 == kinematic; 1 == dynamic(mass-spring); 2 == jump(init kinematic, dynamic for rest of stim)
	 */
	@Override
	public void setZoneModStimType(int stimType) {
		for (int i=0;i<this.zoneMods.length;++i) {
			zoneMods[i].setCurZoneStim(stimType);
		}
	}//setZoneModStimType
	
	////////////////////////
	//original direct zone stimulation functions
	//stimulate zone focii
	private void stimulateOneZone(float stimVal, int zoneIDX, int zonePt, boolean stimMates) {
		//if((flags[isPtsMadeIDX]) && (flags[isZoneMappedIDX])) {
		zonePoints[zoneIDX][zonePt].stimulateZoneKin(stimVal);
		if(stimMates) {	zonePoints[zoneIDX][zonePt].mPt.stimulateZoneKin(stimVal);	}
		//}
	}//stimulateZone
	
	//stimulate zone focii with force from specific frequency bands
	private void stimulateOneZoneForce(float stimVal, int zoneIDX, int zonePt, boolean stimMates) {
		//if((flags[isPtsMadeIDX]) && (flags[isZoneMappedIDX])) {
		zonePoints[zoneIDX][zonePt].stimulateZoneForce(stimVal);
		if(stimMates) {	zonePoints[zoneIDX][zonePt].mPt.stimulateZoneForce(stimVal);	}
		//}
	}//stimulateZone
	
	//excite each zone directly by displacing from rest position by scaled level given in bandVals
	private void stimulateBallKineRand() {
		if(flags[audioValsSetIDX]){
			for(int i=0;i<bandVals.length;++i) {
				int numZones = (i+1)*(i+1);
				for (int j=0;j<numZones;++j) {
					int z = (int) (ThreadLocalRandom.current().nextDouble(0,zonePoints[i].length));
					stimulateOneZone(bandVals[i]*5.0f, i,z, true );
				}
			}
			setFlags(audioValsSetIDX, false);//vals have been processed, clear until new vals have been set
		}
	}//stimulateZone
	
	//DEBUG
	//excite each zone directly by displacing from rest position by scaled level given in bandVals
	private void stimBallTapsKine_DBG() {//[zoneIDX]
		if((beatDetRes[zoneTypIDX]) && (!lastBeatDetRes[zoneTypIDX])) {
			stimulateOneZone(100.0f, zoneTypIDX, zoneMmbrToShow, false);
		} else if(lastBeatDetRes[zoneTypIDX]) {
			resetConfig();		//reset ball shape when beat is gone
		}

	}//stimBallTapsKinematic
	
	private void stimBallTapsMassSprng_DBG() {
		if(beatDetRes[zoneTypIDX]) {
			stimulateOneZoneForce(1000, zoneTypIDX, zoneMmbrToShow, false );
		}
		setAllSpringForce();		
		//solve for all particles
		invokeSolver();
	}//stimBallTapsKinematic
	
	//used to govern how often forces are applied
	private int simCount = 0;
	//TODO need to stimulate ball on beats from music - constant force applications will cause things to explode
	//pass zones to stimulate
	private void stimulateBallMassSpringRand() {
		flags[dispVertHiLiteIDX] = false;
		int beatCount = 5;
		simCount += 1;
		if(flags[audioValsSetIDX])  {
		//if((flags[audioValsSetIDX]) && (simCount%beatCount == 0)) {
			simCount = 0;
			flags[dispVertHiLiteIDX] = true;
			//set force values for all zones - bandVals is array of per-zone levels from song	
			for(int zone=0;zone<bandVals.length;++zone) {
				if((beatDetRes[zone]) && (!lastBeatDetRes[zone]) ) {//send force only when transitioning onto beat
					//TODO determine better mapping to specific zones
					int numZones = (zone+1)*(zone+1);
					for (int j=0;j<numZones;++j) {
						int zoneMbrToShow = (int) (ThreadLocalRandom.current().nextDouble(0,zonePoints[zone].length));
						stimulateOneZoneForce((zone+1) * 50.0f*bandVals[zone], zone,zoneMbrToShow, true );						
					}
				}
			}
			setFlags(audioValsSetIDX, false);//vals have been processed, clear until new vals have been set
		}
		//apply spring forces to return to rest state
		//set force values for all zones - bandVals is array of per-zone levels from song	public void stimulateZone(float stimVal, int zoneIDX, int zonePt, boolean stimMates) {
		setAllSpringForce();		
		//solve for all particles
		invokeSolver();
	}//simulateBall()
	
	//
	private void setAllSpringForce() {
//		for (int i=0;i<numZones;++i) {
//			for(int j=0;j<zonePoints[i].length;++j) {				
//				zonePoints[i][j].stimulateZoneSprFrc();
//			}			
//		}//for all zones	

		myVectorf[] result;		
		for (myRndrdPart part : verts) {
			result = springForce.calcForceOnParticle(part, null, 0);
			float dotProd = result[0]._dot(part.norm);
			part.stimulateFrc(dotProd);
			//part.applyForce(myVectorf._mult(part.norm, dotProd));//todo might need to use result[1] if in wrong direction
		}
	}//setAllSpringForce
	
	//set pointer to new results from band fft, and set loaded to true
	@Override
	public void setFreqVals(float[] _bandRes) {
		bandVals = _bandRes;
		flags[audioValsSetIDX] = bandVals != null;		
	}
	
//	//pass zones to stimulate
//	public void stimulateBallFrc(boolean[] beatDetRes, boolean[] lastBeatDetRes) {
//		flags[dispVertHiLiteIDX] = false;
//		//if((flags[isPtsMadeIDX]) && (flags[isZoneMappedIDX])) { //needs to be checked before called
//		//TODO need to find # of executions corresponding to beats detected
//		int beatCount = 10;
//		simCount += 1;
//		//if(flags[audioValsSetIDX])  {
//		if((flags[audioValsSetIDX]) && (simCount%beatCount == 0)) {
//			simCount = 0;
//			flags[dispVertHiLiteIDX] = true;
//			//set force values for all zones - bandVals is array of per-zone levels from song	
//			for(int i=0;i<bandVals.length;++i) {
//				//if((beatDetRes[i]) ) {//&& (!lastBeatDetRes[i])) {//send force only when transitioning on beat
//				//TODO determine better mapping to specific zones
//				int numZones = (i+1)*(i+1);
//				for (int j=0;j<numZones;++j) {
//					int z = (int) (ThreadLocalRandom.current().nextDouble(0,zonePoints[i].length));
//					stimulateZoneForce(100.0f*bandVals[i], i,z, true );
//				}
//				//}
//			}
//			setFlags(audioValsSetIDX, false);//vals have been processed, clear until new vals have been set
//		}
//		//apply spring forces to return to rest state
//		//set force values for all zones - bandVals is array of per-zone levels from song	public void stimulateZone(float stimVal, int zoneIDX, int zonePt, boolean stimMates) {
//		setAllSpringForce();		
//		//solve for all particles
//		invokeSolver();
//	}//simulateBall()
	@Override
	public void resetConfig() {
		for(myRndrdPart p  : verts) {
			p.reset();
		}		
	}
	
	private void resetVertLocs(int zoneIDX, int zonePt, boolean stimMates) {
		if((flags[isPtsMadeIDX]) && (flags[isZoneMappedIDX])) {
			zonePoints[zoneIDX][zonePt].resetZone();
			if(stimMates) {		zonePoints[zoneIDX][zonePt].mPt.resetZone();	}
		}
	}

	@Override
	//draw ball if it has been inited
	public void drawMe(float timer) {//, int zoneIDX, boolean[] dispFlags) {
	//public void drawMe(float timer, int zoneIDX, boolean showZones, boolean showZoneMates, boolean showNorms) {
		if(!flags[isInitedIDX]){return;}//not made yet
		pa.pushMatrix();pa.pushStyle();
		pa.translate(ctrPart.getCurPos());
		if(flags[dispVertHiLiteIDX]) {
			ctrPart.drawMeWithColor(pa.gui_White);
		}
		if(flags[isPtsMadeIDX]) {
			if(win.getPrivFlags(win.showVertNorms)) {		for(int i=0;i<verts.length;++i) {	verts[i].drawMeWithNorm();	}		} 
			else {				for(int i=0;i<verts.length;++i) {	verts[i].drawMe();}					}
		}
		if(win.getPrivFlags(win.showZones)) {		drawZones(timer, win.zoneToShow, win.getPrivFlags(win.stimZoneMates));	}
		
		pa.popStyle();pa.popMatrix();		
	}//drawMe
	
	private static float accumTime = 0.0f;
	public void drawZones(float timer, int zoneIDX, boolean showZoneMates) {
		accumTime += timer;
		pa.pushMatrix();pa.pushStyle();
		pa.translate(ctrPart.getCurPos());
		//draw zone points
		//if(zonePointsMade) {
		if(showZoneMates) {	for(int i=0;i<zonePoints[zoneIDX].length;++i) {	zonePoints[zoneIDX][i].drawMeMates();}}
		else {				for(int i=0;i<zonePoints[zoneIDX].length;++i) {	zonePoints[zoneIDX][i].drawMe();}}
		//}
		if(flags[isZoneMappedIDX]) {			
			int zone = (int)((accumTime*5) % zonePoints[zoneIDX].length);
			zonePoints[zoneIDX][zone].drawNeighborhood(zone+2);
			if(showZoneMates) {zonePoints[zoneIDX][zone].mPt.drawNeighborhood(zone+3);	}
		}		
		pa.popStyle();pa.popMatrix();		
	}//drawZones
	
		
	//multiplied by radius
	public float[] minZBnds = new float[] {-1.0f,-.5f, -.05f, .05f, .6f, .8f};
	public float[] maxZBnds = new float[] {-.5f, -.05f, .05f, .6f, .8f, 1.0f};
	
	
	private void debugAllZones() {
		float minZ, maxZ;
		ConcurrentSkipListMap<Integer, ArrayList<Integer>> listOfRes = new ConcurrentSkipListMap<Integer, ArrayList<Integer>>();
		for(int zt=0;zt<zonePoints.length;++zt) {//for every type
			minZ = rad * minZBnds[zt];
			maxZ = rad * maxZBnds[zt];
			ArrayList<Integer> tmpListOfRes = new ArrayList<Integer>();
			pa.outStr2Scr("Zone type : " + zt);
			for(int z=0;z<zonePoints[zt].length;++z) {//for every zone
				if((minZ < zonePoints[zt][z].pt.z) && (zonePoints[zt][z].pt.z < maxZ)){
					tmpListOfRes.add(z);
					pa.outStr2Scr("Use point : " + zonePoints[zt][z].debugMeToStr());
				}
			}	
			listOfRes.put(zt, tmpListOfRes);
			pa.outStr2Scr("");
		}
		//display all results
		for(int i=0;i<listOfRes.size();++i) {//for every type
			ArrayList<Integer> listOfZoneIDs = listOfRes.get(i);
			pa.outStr2Scr("Zone type : " + i);
			String res = "IDXs : ";
			for(int z=0;z<listOfZoneIDs.size();++z) {res += ""+ listOfZoneIDs.get(z)+",";}
			pa.outStr2Scr(res);
			pa.outStr2Scr("");
		}
	}//debugAllZones
	
	//debug functions
	public void debug0() {
		debugAllZones();
	};
	public void debug1() {}
	public void debug2() {}
	public void debug3() {}

}//myDancingBall

class myDancingSnowman extends myDancer {	
	public String[] objNames = new String[] {"Head","Torso","Butt","Left Hand","Right Hand"};
	
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


