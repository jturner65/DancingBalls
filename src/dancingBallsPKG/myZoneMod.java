package dancingBallsPKG;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ThreadLocalRandom;

//class to manage zone modification/excitation of a single ball
//each instance manages a single zone type, multiple zones within that type
public abstract class myZoneMod {
	public static DancingBalls pa;
	public myDancingBall ball;
	public int zoneType;
	public int[] zoneIDsToMod;
	//time frame for modding zone
	public float timeToMod;
	//index of primary modification in zones;modification value for currentIdx - used for mods that have reflective bounds (up->down->up etc)
	protected int curIdx, curIdxModVal,
	//# of beats to wait for to apply mod; current beat in cycle;max size for counter used to cycle through zones
		beatsToCount, curBeat, maxSizeForCntr;	
	//reference to array of zone points used by this zone modifier
	protected ConcurrentSkipListMap<Integer, myZonePoint> zonePoints;	

	//stimulator functions, either kinematic, dynamic, or ...
	protected ArrayList<zoneStim> stimFuncs;
	protected int curStimIdx = 0;
	protected zoneStim curStimFunc;
	
	public myZoneMod( DancingBalls _p,myDancingBall _b, int _zt, int[] _zIDXs, int _btc) {
		pa = _p; ball=_b;zoneType=_zt; zoneIDsToMod=_zIDXs;timeToMod=0;
		//change beatsToCount to be related to # of zones being modified, so that each beat cycles forward through the zones
		beatsToCount = _btc;
		zonePoints = new ConcurrentSkipListMap<Integer, myZonePoint>();
		for(int i=0;i<zoneIDsToMod.length;++i) {	zonePoints.put(zoneIDsToMod[i], ball.zonePoints[zoneType][zoneIDsToMod[i]]);}
		maxSizeForCntr = zoneIDsToMod.length;
		curBeat = 0; 
		curIdx = 0;
		curIdxModVal = 1;
	}//ctor
	
	//build array of matched idxs in zone array so that we modify appropriate pairs/sets of points at same time
	protected int[][] buildMultiZoneAra(int numGrps, int numPerGrp){
		int[][] res = new int[numGrps][];
		int idx = 0;
		for(int grp=0;grp<numGrps;++grp) {
			int[] tmpGroup = new int[numPerGrp];
			for(int i=0; i<tmpGroup.length;++i) {tmpGroup[i]=zoneIDsToMod[idx++];}
			res[grp]=tmpGroup;
		}
		zoneIDsToMod = null;//kill this array - only use zoneIDsPerGroup for this zone modifier
		return res;
	}//buildMultiZoneAra
	
	//set zone stim func type - 0==kinematic, 1==dynamic, 2== ? etc.
	public void setCurZoneStim(int _typ) {
		curStimIdx = _typ;
		curStimFunc = stimFuncs.get(curStimIdx);
	}
	
	//call every frame to modify zone - each zone will manage whether it is actually modified or not
	public void modZone(float _modAmt, boolean _beatDet, boolean _lastBeatDet) {		
		if (_beatDet) {																//beat has been detected
			if(!_lastBeatDet) {							startApplyMod(_modAmt);}	//rising edge of start of beat
			else {										applyMod(_modAmt);	}		//during beat
		} else if ((!_beatDet) && (_lastBeatDet)) {		endApplyMod();}				//trailing edge of beat
		//here is space between end of one beat and start of next
	}//modZone			
	//reset all points in zone and zone mod variables
	protected void resetZone() {
		curBeat = 0; 
		curIdx = 0;
		curIdxModVal = 1;		
		for (myZonePoint zp : zonePoints.values()) {	zp.resetZone();}
	}//resetZone
	
	//start the mod application cycle
	protected void startApplyMod(float _mod) {
		//any global beginning-of-the-beat modification code
		startApplyModIndiv(_mod);
	}	
	
	//end the mod application cycle, and cycle to next mod target if curBeat == beatsToCount
	protected void endApplyMod() {
		//increment curbeat
		curBeat = (curBeat + 1) % beatsToCount;
		//check if cycled through
		endApplyModIndiv(curBeat == 0);
	}
	
	//individual zone modifiers functions
	protected abstract void applyMod(float _modAmt);
	protected abstract void startApplyModIndiv(float _modAmt); 
	protected abstract void endApplyModIndiv(boolean cycled); 

}//class myZoneMod


//modify groups of zones simultaneously, oscillating forward
class altGroupZoneMod extends myZoneMod{
	private int[][] zoneIDsPerGroup;
	
	//numGrps == # of groupings of equal size of zone points
	public altGroupZoneMod(DancingBalls _p, myDancingBall _b, int _zt, int[] _zIDXs, int _btc, int numGrps) {
		super(_p, _b, _zt, _zIDXs, _btc);
		//numGrps should be divisor of zoneIDsToMod.length so that equal size groups result
		zoneIDsPerGroup = buildMultiZoneAra(numGrps,zoneIDsToMod.length/numGrps);
		maxSizeForCntr = zoneIDsPerGroup[0].length;
	}

	@Override
	protected void applyMod(float _modAmt) {
		for(int grp=0;grp<zoneIDsPerGroup.length;++grp) {
			curStimFunc.stimZone(zonePoints.get(zoneIDsPerGroup[grp][curIdx]), _modAmt);
			//zonePoints.get(zoneIDsPerGroup[grp][curIdx]).stimulateZoneForce(_modAmt);	
		}		
	}

	@Override
	//stuff to do when beat first hits
	protected void startApplyModIndiv(float _modAmt) {
		for(int grp=0;grp<zoneIDsPerGroup.length;++grp) {
			curStimFunc.startStimZone(zonePoints.get(zoneIDsPerGroup[grp][curIdx]), _modAmt);
		}		
	}

	@Override
	//stuff to do after beat has ended
	protected void endApplyModIndiv(boolean cycled) {
		if(cycled) {	curIdx = (curIdx + 1) % maxSizeForCntr;	}
	}
	
}//class cycleGroupZoneMod

//modify groups of zones simultaneously, oscillating forward and backward
class cycleGroupZoneMod extends myZoneMod{
	private int[][] zoneIDsPerGroup;
	
	//numGrps == # of groupings of equal size of zone points
	public cycleGroupZoneMod(DancingBalls _p, myDancingBall _b, int _zt, int[] _zIDXs, int _btc, int numGrps) {
		super(_p, _b, _zt, _zIDXs, _btc);
		//numGrps should be divisor of zoneIDsToMod.length so that equal size groups result
		zoneIDsPerGroup = buildMultiZoneAra(numGrps,zoneIDsToMod.length/numGrps);
		maxSizeForCntr = zoneIDsPerGroup[0].length;
	}

	@Override
	protected void applyMod(float _modAmt) {
		for(int grp=0;grp<zoneIDsPerGroup.length;++grp) {
			curStimFunc.stimZone(zonePoints.get(zoneIDsPerGroup[grp][curIdx]), _modAmt);
			//zonePoints.get(zoneIDsPerGroup[grp][curIdx]).stimulateZoneForce(_modAmt);	
		}		
	}

	@Override
	//stuff to do when beat first hits
	protected void startApplyModIndiv(float _modAmt) {
		for(int grp=0;grp<zoneIDsPerGroup.length;++grp) {
			curStimFunc.startStimZone(zonePoints.get(zoneIDsPerGroup[grp][curIdx]), _modAmt);
		}		
	}

	@Override
	//stuff to do after beat has ended
	protected void endApplyModIndiv(boolean cycled) {
		//cycle current zone point index, either reflecting at boundaries, or wrap increasing wrap around
		if(cycled) {	
			if((curIdx == maxSizeForCntr-1) || (curIdx == 0)) {curIdxModVal *=-1;}//change direction if at size bounds			
			curIdx = (curIdx + curIdxModVal);
		}				
	}
	
}//class cycleGroupZoneMod

//modify 3 verts at a time
class randMultiGroupZoneMod extends myZoneMod{
	private int[][] zoneIDsPerStimGrp;
	private int numVertsToSimulMod;

	public randMultiGroupZoneMod(DancingBalls _p, myDancingBall _b, int _zt, int[] _zIDXs, int _btc, int _numVertsToSimulMod) {
		super(_p, _b, _zt, _zIDXs, _btc);
		numVertsToSimulMod = _numVertsToSimulMod;
		zoneIDsPerStimGrp = buildMultiZoneAra(zoneIDsToMod.length/numVertsToSimulMod,numVertsToSimulMod);
		maxSizeForCntr = zoneIDsPerStimGrp.length;
	}

	@Override
	protected void applyMod(float _modAmt) {
		for(int v=0;v<numVertsToSimulMod;++v) {//mod a single group of vertices
			curStimFunc.stimZone(zonePoints.get(zoneIDsPerStimGrp[curIdx][v]), _modAmt);
			//zonePoints.get(zoneIDsPerStimGrp[curIdx][v]).stimulateZoneForce(_modAmt);	
		}		
	}

	@Override
	//stuff to do when beat first hits
	protected void startApplyModIndiv(float _modAmt) {
		for(int v=0;v<numVertsToSimulMod;++v) {//mod a single group of vertices
			curStimFunc.startStimZone(zonePoints.get(zoneIDsPerStimGrp[curIdx][v]), _modAmt);
		}		
	}

	@Override
	//stuff to do after beat has ended
	protected void endApplyModIndiv(boolean cycled) {
		if(cycled) {curIdx = ThreadLocalRandom.current().nextInt(maxSizeForCntr);	}//randomly pick another group to stimulate		
	}
	
}//class randMultiGroupZoneMod

//classes to manage function calling zone stimulator - appropriate function call depending on what type of stimulation we have set
abstract class zoneStim{
	public zoneStim(){}
	//anything special to perform at beginning of beat 
	public abstract void startStimZone(myZonePoint _p, float _modAmt);
	//actual stimulation of zone
	public abstract void stimZone(myZonePoint _p,float _modAmt);
}//class zoneStim

class kinZoneStim extends zoneStim{
	public kinZoneStim() {	super();}
	@Override
	public void startStimZone(myZonePoint _p,float _modAmt) {//special setup to perform for this type of stimulation function
		_p.stimulateZoneKin(_modAmt);
	}
	@Override
	public void stimZone(myZonePoint _p,float _modAmt) {_p.stimulateZoneKin(_modAmt);}
}//class kinZoneStim

class dynZoneStim extends zoneStim{
	public dynZoneStim() {	super();}
	@Override
	public void startStimZone(myZonePoint _p,float _modAmt) {//special setup to perform for this type of stimulation function
		_p.stimulateZoneForce(_modAmt);
	}
	@Override
	public void stimZone(myZonePoint _p,float _modAmt) {_p.stimulateZoneForce(_modAmt);}
}//class dynZoneStim

class jumpZoneStim extends zoneStim{//starts with kinematic displacement, then continues with dynamic mass-spring
	public jumpZoneStim() {	super();}
	@Override
	public void startStimZone(myZonePoint _p,float _modAmt) {//special setup to perform for this type of stimulation function - kinematic displacement
		_p.stimulateZoneKin(_modAmt);
	}
	@Override
	public void stimZone(myZonePoint _p,float _modAmt) {_p.stimulateZoneForce(_modAmt);}
	
}//class jumpZoneStim
