package dancingBallsPKG;

import java.util.*;
import java.util.concurrent.*;

/**
 * object representing a moving, deforming ball
 * @author john
 */
public class myDancingBall {
	public DancingBalls pa;
	public DancingBallWin win;
	public String name;
	public static int baseID = 0;
	public int ID;
	
	//integer rep of ball color - modify this to reflect response to music
	public int ballClr = DancingBalls.gui_White;
	//at rest radius, mass of center(multiple of each vert's mass)
	public float rad, ctrMass = 10.0f;
	
	//list of vertices of dancing ball
	public myRndrdPart[] verts;
	public myRndrdPart ctrPart;
	public myForce[] dfltForces; 
	
	//spring to rest position for all particles
	public mySpringToRest springForce;
	public mySpringToRest[] springsPerZone;
	//constants - manage via UI?
	public double sprKp = 50.0, sprKd = .8*sprKp;
	
	//fft data
	public float[] bandVals;	
	
	//beat timer
	//public float[] beatTimes;
	
	//UI selected zone and zone member to show being displaced - for debugging
	public int zoneTypIDX;
	public int zoneMmbrToShow;
	//current and last frame per-zone beat detection results
	public boolean[] beatDetRes;
	public boolean[] lastBeatDetRes;

	//k values for each zone so they resonate at frequency of zone in music
	public float[] zoneKVals;
	
	//neighborhoods for each of n "zones" of ball surface to displace from musical content - corresponds to # of frequency spans to respond to
	public static final int numZones = DancingBallWin.numZones;//set in owning window
	//set n to be 6 - 6 spans of frequencies in the music, to correspond to 6 different zone maps of sphere surface
	
	//array of per zone # to zone focal points for each zone - build 1 time
	public final myZonePoint[][] zonePoints = new myZonePoint[numZones][];
	//precalced random perturbation to use when calculating distances, to minimize collisions 
	public static final float[] zonePtsDel = new float[1000];
	//array of number of zone focus verts for each zone - few focus points mean large neighborhood of verts in zone 
	//low freq to high freq
	//public int[] numZoneVerts = new int[] {4,8,32,128,512,2048};
	public int[] numZoneVerts = new int[] {8,16,32,64,128,256};
	
	//stimulate zones based on zone location - this is per zone array of zone IDs to stimulate
	public int[][] zonesToStim = new int[][] {
		{6,7},									//lowest freq - alt between 2 : altGroupZoneMod with group size 1		
		{13,8,9,
		12,11,10},								//low bass freq - oscillate/wave through 1st half and 2nd half of array simultaneously, from back to front (in idx order) : 			

		{14,15,16,17,18, 
		13,12,11,20,19},						//low melody - pair opposite zones front to back, and then back to front : cycleGroupZoneMod
		
		{12,13,14,15,16,17,18,19,20,21,22,23,24},	//high melody - cycle through entire set in order, and then back again descending		: cycleIndivZoneMod
		
		{15,14,13,
		16,17,18,
		22,21,20,
		23,24,25},								//low range high freq - stimulate 4 groups simultaneously in order (ignore 12 and 19 to make even)	: cycleGroupZoneMod
		
		{0,1,2,
		4,14,13,
		5,15,16,
		7,18,19,
		11,26,25,
		9,21,22,
		10,23,24}								//high range high freq - 3 layers, stimulate in groups of 3 (stimulate sets of 3 idxs at a time) : randMultiGroupZoneMod
		};
	
	//record of most recent modifications to each zone's values	
	public myZoneMod[] zoneMods;
	//per-zone count of beats before zone modification cycles to next zone member
	public final int[] zmBeatCounts = new int[] {2,4,2,2,2,2};
	//per zone # of groupings for zone mod handling - each group member at a particular idx is stimulated at the same time
	public final int[] zmGroupSize = new int[] {1,2,2,1,4,7};
	
	public float scaleZVal;//change to make an ellipsoid along z axis - set in init
	
	public boolean[] flags;
	public static final int 
		debugIDX 		= 0,		//debug mode
		isPtsMadeIDX 	= 1,		//are points describing sphere made?
		isZoneMappedIDX = 2,		//are zones mapped on sphere?
		isInitedIDX	    = 3,		//all all initialization procedures complete?
		audioValsSetIDX	= 4,		//if true, audio values from fft are set
		dispVertHiLiteIDX = 5;		//display highlights in verts to show something has happened
	public static final int numFlags = 6;
	
	public myDancingBall(DancingBalls _p, DancingBallWin _win, String _name, myVectorf _ctrStart,float _rad, float _scaleZVal) {
		pa = _p; win = _win; name = _name;	ID = baseID++;scaleZVal = _scaleZVal;
		ctrPart = new myRndrdPart(pa, this, _ctrStart, new myVectorf(), new myVectorf(), SolverType.RK4, new myVectorf(), ballClr, ctrMass);
		rad = _rad;
		initFlags();
		buildZonePoints();	
	}//myDancingBall
	private void initFlags() {flags = new boolean[numFlags];for(int i=0;i<numFlags;++i) {flags[i]=false;}}
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
	
	//remake ball if need to change # of zones
	private void buildZonePoints() {
		for(int i=0;i<zonePtsDel.length; ++i){zonePtsDel[i] = (float) (1.0f + ThreadLocalRandom.current().nextDouble(-.001, .001));}
		for(int i=0;i<numZones;++i) {	
			myVectorf [][] zPtsLocs = pa.getRegularSphereList(rad, numZoneVerts[i], scaleZVal);//idx 0 is norm, idx 1 is location
			myZonePoint[] tmp = new myZonePoint[zPtsLocs.length];
			for(int j=0;j<zPtsLocs.length;++j) {		tmp[j] = new myZonePoint(pa,i,j,zPtsLocs[j][1]);	}
			zonePoints[i] = tmp;
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
	}//buildZonePoints
	
	//set k values for resonant frequencies for each zone, using the frequency of the beats at that zone
	//where frequency == f and sqrt(k/m) = 2pif -> k = 4*(pi*f)^2
	//zoneFreqs is array of frequencies of beats for each zone
	private final float fourPiSq = 4*pa.PI*pa.PI;
	public void setZoneKs(float[] zoneFreqs) {
		float ks;
		for(int i=0;i<zoneFreqs.length;++i) {
			ks = fourPiSq*zoneFreqs[i]*zoneFreqs[i];
			springsPerZone[i].setConstVal1(ks);
			springsPerZone[i].setConstVal2(.8*ks);
		}
		//pa.outStr2Scr("idx 0 beat freq :  " + tapBeats[0].getBeatFreq());		
	}
	
	public void setZoneKs() {//TODO replace this with above, once we have fequency of beats in zones
	}
		
	/**
	 * build vertices and normals of vertices for this sphere - use this to rebuild sphere
	 * @param _ctrStart : where this sphere should start
	 * @param _rad : sphere radius
	 * @param numParts : number of particle verts for this sphere
	 * @param randVerts : should verts be spaced randomly or uniformly over surface of sphere
	 */
	public void buildVertsAndNorms(int numParts, boolean randVerts) {	buildVertsAndNorms(rad, numParts, randVerts);	}
	public void buildVertsAndNorms(float _rad, int numParts, boolean randVerts) {	
		rad = _rad;
		//treat center as 0,0,0
		setFlags(isPtsMadeIDX,false);
		setFlags(isInitedIDX,false);
		setFlags(isZoneMappedIDX,false);		//set true in zone mapping functions in thread call
		win.setBallIsMade(false);
		ArrayList<myRndrdPart> vertAra = new ArrayList<myRndrdPart>();
		myVectorf[][] tmpPosNorm = new myVectorf[numParts][];
		myVectorf tmpCtr = new myVectorf(0,0,0);
		//build particles centered around origin - ctrPart's world position will determine location of ball, once ball starts to move
		if(randVerts) {
			for(int i=0;i<numParts;++i) {	tmpPosNorm[i] = pa.getRandPosOnSphere(rad, tmpCtr,scaleZVal);		}			
		} else { //build sphere of regularly spaced points
			tmpPosNorm = pa.getRegularSphereList(rad, numParts,scaleZVal);
			numParts = tmpPosNorm.length;//might not be the requested #
		}
		for(int i=0;i<numParts;++i) {vertAra.add(new myRndrdPart(pa, this, tmpPosNorm[i][1], new myVectorf(), new myVectorf(), SolverType.RK4, tmpPosNorm[i][0], ballClr));}			

		
		System.out.println("Setting verts : " + vertAra.size() + " win :"  +win.name);
		verts = vertAra.toArray(new myRndrdPart[0]);
		setFlags(isPtsMadeIDX, true);
		//launch thread to get closest verts to zones 
		pa.th_exec.execute(new myDanceBallMapper(this));
	}//buildVertsAndNorms
	
	//move the center particle around to the music
	public void modCtrParticle(myVectorf mod) {

	}//modCtrParticle

	
	//initialize components necessary once mapping is finished
	public void finalInit() {
		pa.outStr2Scr("Ball Final Init called");		
		if((!flags[isZoneMappedIDX]) || (flags[isInitedIDX])) {return;}
		//buildDefForces("dancingBall",-4.0);
		//(DancingBalls _p, String _n, double _k,double _k2)
		springsPerZone = new mySpringToRest[numZones];
		for(int i=0;i<numZones;++i) {
			springsPerZone[i]=new mySpringToRest(pa, "Spring To Rest Zone "+i, sprKp, sprKd);
		}
		springForce = new mySpringToRest(pa, "Spring To Rest", sprKp, sprKd);
		
		zoneMods = new myZoneMod[numZones];
		zoneMods[0] = new altGroupZoneMod(pa, this, 0, zonesToStim[0], zmBeatCounts[0], zmGroupSize[0]);
//		zoneMods[1] = new altZoneMod(pa, this, 0, zonesToStim[0],beatCount);
		
//		//(DancingBalls _p, myDancingBall _b, int _zt, int[] _zIDXs, int _btc)
//		//beat count == # of beats before cycling to different zone
//		int beatCount = 4;
//		zoneMods[0] = new altZoneMod(pa, this, 0, zonesToStim[0],beatCount);
//		zoneMods[1] = new altZoneMod(pa, this, 0, zonesToStim[0],beatCount);
				
//		{13,8,9,
//		12,11,10},								//low bass freq - oscillate/wave through 1st half and 2nd half of array simultaneously, from back to front : cycleGroupZoneMod			
//
//		{14,15,16,17,18, 
//		13,12,11,20,19},						//low melody - pair opposite zones front to back, and then back to front : cycleGroupZoneMod
//		
//		{12,13,14,15,16,17,18,19,20,21,22,23,24},	//high melody - cycle through entire set in order	: altZoneMod
//		
//		{15,14,13,
//		16,17,18,
//		22,21,20,
//		23,24,25},								//low range high freq - stimulate 4 groups simultaneously in order (ignore 12 and 19 to make even)	: cycleGroupZoneMod
//		
//		{0,1,2,
//		4,14,13,
//		5,15,16,
//		7,18,19,
//		11,26,25,
//		9,21,22,
//		10,23,24}								//high range high freq - 3 layers, stimulate in groups of 3 (stimulate sets of 3 idxs at a time) : randMultiGroupZoneMod
		
		pa.outStr2Scr("Ball Final Init Done");		
		setFlags(isInitedIDX, true);	
		win.setBallIsMade(true);
	}//finalInit
	
	//call after all forces have been added
	private void invokeSolver() {for (int idx = 0; idx < verts.length; ++idx) {verts[idx].integAndAdvance(win.deltaT);}}
	
	//called by owning class to set variables necessary for simulation, either kinematic or mass-spring
	public void setBallSimVals(int _zs, int _zMbr,boolean[] _beatDetRes, boolean[] _lastBeatDetRes) {
		//these two are debug-related
		zoneTypIDX = _zs;
		zoneMmbrToShow = _zMbr;
		beatDetRes = _beatDetRes;
		lastBeatDetRes = _lastBeatDetRes;
	}//setBallSimVals
	
	//combine kinematic and dynamic stimualtion
//	public void stimKinAndDyn() {
//		
//	}
	
	//modAmtMillis is time passed per frame in milliseconds
	public void simMe(float modAmtMillis, boolean stimTaps, boolean useFrc) {
		if(stimTaps) {//stimulate ball with finger taps/ detected beats - intended to be debugging mechanism
			if(useFrc){		stimBallTapsMassSprng_DBG();} 
			else {			stimBallTapsKine_DBG();}//use force deformations or kinematic deformations				
		} else {//stimulate with pure audio
			if(useFrc){		stimulateBallMassSpring();} //use force deformations
			else {			resetVertLocs();stimulateBallKine();}//use kinematic deformations
		}		
	}//simMe	
	
	//stimulate zone focii
	private void stimulateOneZone(float stimVal, int zoneIDX, int zonePt, boolean stimMates) {
		//if((flags[isPtsMadeIDX]) && (flags[isZoneMappedIDX])) {
			zonePoints[zoneIDX][zonePt].stimulateZoneKin(stimVal);
			if(stimMates) {	zonePoints[zoneIDX][zonePt].mPt.stimulateZoneKin(stimVal);	}
		//}
	}//stimulateZone
	
	//stimulate zone focii with force from specific frequency bands
	private void stimulateZoneForce(float stimVal, int zoneIDX, int zonePt, boolean stimMates) {
		//if((flags[isPtsMadeIDX]) && (flags[isZoneMappedIDX])) {
			zonePoints[zoneIDX][zonePt].stimulateZoneForce(stimVal);
			if(stimMates) {	zonePoints[zoneIDX][zonePt].mPt.stimulateZoneForce(stimVal);	}
		//}
	}//stimulateZone
		
	//excite each zone directly by displacing from rest position by scaled level given in bandVals
	public void stimulateBallKine() {
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
	public void stimBallTapsKine_DBG() {//[zoneIDX]
		if((beatDetRes[zoneTypIDX]) && (!lastBeatDetRes[zoneTypIDX])) {
			stimulateOneZone(100.0f, zoneTypIDX, zoneMmbrToShow, false);
		} else if(lastBeatDetRes[zoneTypIDX]) {
			resetVertLocs();		//reset ball shape when beat is gone
		}

	}//stimBallTapsKinematic
	
	public void stimBallTapsMassSprng_DBG() {
		if(beatDetRes[zoneTypIDX]) {
			stimulateZoneForce(1000, zoneTypIDX, zoneMmbrToShow, false );
		}
		setAllSpringForce();		
		//solve for all particles
		invokeSolver();
	}//stimBallTapsKinematic
	
	//used to govern how often forces are applied
	private int simCount = 0;
	//TODO need to stimulate ball on beats from music - constant force applications will cause things to explode
	//pass zones to stimulate
	public void stimulateBallMassSpring() {
		flags[dispVertHiLiteIDX] = false;
		int beatCount = 10;
		simCount += 1;
		//if(flags[audioValsSetIDX])  {
		if((flags[audioValsSetIDX]) && (simCount%beatCount == 0)) {
			simCount = 0;
			flags[dispVertHiLiteIDX] = true;
			//set force values for all zones - bandVals is array of per-zone levels from song	
			for(int zone=0;zone<bandVals.length;++zone) {
				//if((beatDetRes[i]) ) {//&& (!lastBeatDetRes[i])) {//send force only when transitioning on beat
				//TODO determine better mapping to specific zones
				int numZones = (zone+1)*(zone+1);
				for (int j=0;j<numZones;++j) {
					int zoneMbrToShow = (int) (ThreadLocalRandom.current().nextDouble(0,zonePoints[zone].length));
					stimulateZoneForce(100.0f*bandVals[zone], zone,zoneMbrToShow, true );
				}
				//}
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
		myVectorf[] result;
//		for(int i=0;i<bandVals.length;++i) {
//			int numZones = (i+1)*(i+1);
//			for (int j=0;j<numZones;++j) {
//				int z = (int) (ThreadLocalRandom.current().nextDouble(0,zonePoints[i].length));
//				stimulateZoneForce(100.0f*bandVals[i], i,z, true );
//			}
//		}
		
		for (myRndrdPart part : verts) {
			result = springForce.calcForceOnParticle(part, null, 0);
			//part.applyForce(result[0]);//todo might need to use result[1] if in wrong direction
			float dotProd = result[0]._dot(part.norm);
			part.applyForce(myVectorf._mult(part.norm, dotProd));//todo might need to use result[1] if in wrong direction
		}
	}//setAllSpringForce
	
	//set pointer to new results from band fft, and set loaded to true
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

	public void resetVertLocs() {
		for(myRndrdPart p  : verts) {
			p.reset();
		}		
	}
	
	public void resetVertLocs(int zoneIDX, int zonePt, boolean stimMates) {
		if((flags[isPtsMadeIDX]) && (flags[isZoneMappedIDX])) {
			zonePoints[zoneIDX][zonePt].resetZone();
			if(stimMates) {		zonePoints[zoneIDX][zonePt].mPt.resetZone();	}
		}
	}

	//draw ball if it has been inited
	public void drawMe(float timer, int zoneIDX, boolean showZones, boolean showZoneMates, boolean showNorms) {
		if(!flags[isInitedIDX]){return;}//not made yet
		pa.pushMatrix();pa.pushStyle();
		pa.translate(ctrPart.getCurPos());
		if(flags[dispVertHiLiteIDX]) {
			ctrPart.drawMeWithColor(pa.gui_White);
		}
		if(flags[isPtsMadeIDX]) {
			if(showNorms) {		for(int i=0;i<verts.length;++i) {	verts[i].drawMeWithNorm();	}		} 
			else {				for(int i=0;i<verts.length;++i) {	verts[i].drawMe();}					}
		}
		if(showZones) {		drawZones(timer, zoneIDX, showZoneMates);	}
		
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
	
	
	public void debugAllZones() {
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


}//myDancingBall


//class to hold a zone focal point
class myZonePoint{
	public static DancingBalls pa;
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
	
	public myZonePoint(DancingBalls _pa, int _zIdx, int _zID, myVectorf _pt){
		pa = _pa;pt = _pt;zoneIDX = _zIdx; zoneID = _zID;
		zoneMrkrSz = pa.max(1.0f,10.0f - 2*zoneIDX); 
		drawPt = new myVectorf(pt); drawPt._mult(2.0f);
	}
	
	public void setMate(myZonePoint _mate) {mPt = _mate; _mate.mPt = this;mateSet=true; _mate.mateSet = true;}
	
	public void setNeighborhood(ConcurrentNavigableMap<Float,myRndrdPart> _ngbhd) {
		ngbhd = _ngbhd;
		minDist = ngbhd.firstKey();
		maxDist = ngbhd.lastKey();	
		interpDenom = 1.0f/(maxDist - minDist);
	}

	//stimulate zone with stim value, scaled for distance from zone point
	public void stimulateZoneKin(float stim) {
		for(Float key : ngbhd.keySet()) {
			float stimVal = stim * (1 - ((key - minDist)*interpDenom));
			//System.out.println("for initial stim : " + stim + " and for dist : " + key + " stim val is " + stimVal);
			ngbhd.get(key).stimulate(stimVal);
		}	
	}//stimulateZone

	//stimulate a zone with a force, scaled for distance from zone point - add force in direction of normal
	public void stimulateZoneForce(float stim) {//
		for(Float key : ngbhd.keySet()) {
			float stimVal = stim * (1 - ((key - minDist)*interpDenom));
			//System.out.println("for initial stim : " + stim + " and for dist : " + key + " stim val is " + stimVal);
			ngbhd.get(key).stimulateFrc(stimVal);
		}			
	}//stimulateZoneForce	
	
//	public void applyZoneSpringForce(mySpringToRest frc) {
//		myVectorf[] result;
//		for(myRndrdPart part : ngbhd.values()) {		
//			result = frc.calcForceOnParticle(part, null, 0);//d is 0 because we want to meet rest position exactly
//			part.stimulateFrc(result[0].magn);//applyForce(result[0]);//todo might need to use result[1] if in wrong direction
//		}					
//	}//applyZoneSpringForce	
	
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

