package dancingBallsPKG;

import java.util.concurrent.ConcurrentSkipListMap;

//class to manage zone modification/excitation
public abstract class myZoneMod {
	public static DancingBalls pa;
	public myDancingBall ball;
	public int zoneType;
	public int[] zoneIDsToMod;
	//time frame for modding zone
	public float timeToMod;
	//index of primary modification in zones
	protected int currentIdx;
	//reference to array of zone points used by this zone modifier
	//public myZonePoint[] zonePoints;
	protected ConcurrentSkipListMap<Integer, myZonePoint> zonePoints;
	
	//# of beats to wait for to apply mod
	protected int beatsToCount, curBeat;
	//modification value for currentIdx - used for mods that have reflective bounds (up->down->up etc)
	protected int curIdxModVal;

	
	public myZoneMod( DancingBalls _p,myDancingBall _b, int _zt, int[] _zIDXs, int _btc) {
		pa = _p; ball=_b;zoneType=_zt; zoneIDsToMod=_zIDXs;timeToMod=0;
		//change beatsToCount to be related to # of zones being modified, so that each beat cycles forward through the zones
		beatsToCount = _btc;
		zonePoints = new ConcurrentSkipListMap<Integer, myZonePoint>();
		for(int i=0;i<zoneIDsToMod.length;++i) {
			zonePoints.put(zoneIDsToMod[i], ball.zonePoints[zoneType][zoneIDsToMod[i]]);
		}
		curBeat = 0; 
		curIdxModVal = 1;
	}//ctor
	//build array of matched idxs in zone array so that we modify appropriate pairs/sets of points at same time
	protected int[][] buildMultiZoneAra(int numGrps, int numPerGrp){
		int[][] res = new int[numGrps][];
		int idx = 0;
		for(int grp=0;grp<numGrps;++grp) {
			int[] tmpGroup = new int[numPerGrp];
			for(int i=0; i<tmpGroup.length;++i) {
				tmpGroup[i]=zoneIDsToMod[idx++];
			}
			res[grp]=tmpGroup;
		}
		zoneIDsToMod = null;//kill this array - only use zoneIDsPerGroup for this modifier
		return res;
	}
	
	//call every frame to modify zone - each zone will manage whether it is actually modified or not
	public void modZone(float _modAmt, boolean _beatDet, boolean _lastBeatDet) {
		//beat has been detected
		if (_beatDet) {
			if(!_lastBeatDet) {startApplyMod(_modAmt);}//rising edge of start of beat
			else {				applyMod(_modAmt);	}
		} else if ((!_beatDet) && (_lastBeatDet)) {//trailing edge of beat
			endApplyMod();
		}	
		//here no beat is detected
	}//modZone			
	//reset all points in zone
	protected void resetZone() {for (myZonePoint zp : zonePoints.values()) {	zp.resetZone();}};
	
	//start the mod application cycle
	protected void startApplyMod(float _mod) {
		//any global beginning-of-the-beat modification code
		startApplyModIndiv(_mod);
	}	
	//end the mod application cycle
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
	
	//cycle current zone point index, either reflecting at boundaries, or wrap increasing wrap around
	protected void cycleCurIDXReflective(boolean cycled, int maxSize) {
		if(cycled) {	
			if((currentIdx == maxSize-1) || (currentIdx == 0)) {
				curIdxModVal *=-1;//change direction
			}
			currentIdx = (currentIdx + curIdxModVal);
		}				
	}//cycleCurIDXReflective
	
	protected void cycleCurIDXWrap(boolean cycled, int maxSize) {
		if(cycled) {	currentIdx = (currentIdx + 1) % maxSize;	}
	}

}//class myZoneMod

//class to manage alternating/increasing zone modification
class altZoneMod extends myZoneMod{	
	public altZoneMod(DancingBalls _p, myDancingBall _b, int _zt, int[] _zIDXs, int _btc) {
		super(_p, _b, _zt, _zIDXs, _btc);
		currentIdx = 0;
	}


	@Override
	protected void applyMod(float _modAmt) {
		zonePoints.get(zoneIDsToMod[currentIdx]).stimulateZoneForce(_modAmt);		
	}
	@Override
	protected void startApplyModIndiv(float _modAmt) {
		applyMod(_modAmt);
		
	}

	//end the application of modification - use this to increment 
	@Override
	protected void endApplyModIndiv(boolean cycled) {
		//change index of next modification to be other idx if cycled
		cycleCurIDXWrap(cycled, zoneIDsToMod.length);
		
	}//


}//altZoneMod

//cycle through sequence of individual zones to be modded in either ascending or descending order
class cycleIndivZoneMod extends myZoneMod{
	
	public cycleIndivZoneMod(DancingBalls _p, myDancingBall _b, int _zt, int[] _zIDXs, int _btc) {
		super(_p, _b, _zt, _zIDXs, _btc);
		curIdxModVal= 1;
	}


	@Override
	protected void applyMod(float _modAmt) {
		zonePoints.get(zoneIDsToMod[currentIdx]).stimulateZoneForce(_modAmt);		
	}

	@Override
	protected void startApplyModIndiv(float _modAmt) {
		applyMod(_modAmt);		
	}


	@Override
	protected void endApplyModIndiv(boolean cycled) {
		cycleCurIDXReflective( cycled, zoneIDsToMod.length);
	}	
}//cycleIndivZoneMod

//modify groups of zones simultaneously, oscillating forward and backward
class cycleGroupZoneMod extends myZoneMod{
	private int[][] zoneIDsPerGroup;
	
	//numGrps == # of groupings of equal size of zone points
	public cycleGroupZoneMod(DancingBalls _p, myDancingBall _b, int _zt, int[] _zIDXs, int _btc, int numGrps) {
		super(_p, _b, _zt, _zIDXs, _btc);
		//numGrps should be divisor of zoneIDsToMod.length so that equal size groups result
		zoneIDsPerGroup = buildMultiZoneAra(numGrps,zoneIDsToMod.length/numGrps);
	}

	@Override
	protected void applyMod(float _modAmt) {
		for(int grp=0;grp<zoneIDsPerGroup.length;++grp) {
			zonePoints.get(zoneIDsPerGroup[grp][currentIdx]).stimulateZoneForce(_modAmt);	
		}		
	}

	@Override
	protected void startApplyModIndiv(float _modAmt) {
		applyMod(_modAmt);	
		
	}

	@Override
	protected void endApplyModIndiv(boolean cycled) {
		cycleCurIDXReflective( cycled, zoneIDsPerGroup[0].length);//all groups are same size	
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
	}

	@Override
	protected void applyMod(float _modAmt) {
		for(int v=0;v<numVertsToSimulMod;++v) {//mod a single group of vertices
			zonePoints.get(zoneIDsPerStimGrp[currentIdx][v]).stimulateZoneForce(_modAmt);	
		}		
	}

	@Override
	protected void startApplyModIndiv(float _modAmt) {
		applyMod(_modAmt);			
	}

	@Override
	protected void endApplyModIndiv(boolean cycled) {
		cycleCurIDXWrap( cycled, zoneIDsPerStimGrp.length);//through every group
		
	}
	
}//class randMultiGroupZoneMod