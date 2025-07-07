package dancingBallsPKG;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ThreadLocalRandom;

//class to manage zone modification/excitation of a single ball
//each instance manages a single zone type, multiple zones within that type
public class myZoneMod {
    public static DancingBalls pa;
    public myDancingBall ball;
    public int zoneType;
    //per-group listing of zone idxs to modify
    protected int[][] zoneIDsPerGroup;
    //map of zone points used by this zone modifier, keyed by zone idx
    protected ConcurrentSkipListMap<Integer, myZonePoint> zonePoints;    

    //time frame for modding zone
    public float timeToMod;
    //index of primary modification in zones;modification value for currentIdx - used for mods that have reflective bounds (up->down->up etc)
    protected int curIdx, curIdxModVal,
    //# of beats to wait for to apply mod; current beat in cycle;max size for counter used to cycle through zones
        beatsToCount, curBeat, maxSizeForCntr;
    //what kind of cycling this mod performs - 0 : increase, 1 : increase/decrease, 2 : random
    protected int zoneModCylTyp;

    //stimulator functions, either kinematic, dynamic, or ...
    protected ArrayList<zoneStim> stimFuncs;
    protected int curStimIdx = 0;
    protected zoneStim curStimFunc;
    
    public myZoneMod( DancingBalls _p,myDancingBall _b, int _zt, int[] _zIDXs, int _btc, int _numGrps, int _znModType) {
        pa = _p; ball=_b;zoneType=_zt; timeToMod=0; zoneModCylTyp =_znModType;
        //change beatsToCount to be related to # of zones being modified, so that each beat cycles forward through the zones
        beatsToCount = _btc;
        zonePoints = new ConcurrentSkipListMap<Integer, myZonePoint>();
        for(int i=0;i<_zIDXs.length;++i) {    zonePoints.put(_zIDXs[i], ball.zonePoints[zoneType][_zIDXs[i]]);}
        
        zoneIDsPerGroup = buildMultiZoneAra(_zIDXs, _numGrps);
        maxSizeForCntr = zoneIDsPerGroup[0].length;
        curBeat = 0; 
        curIdx = 0;
        curIdxModVal = 1;
        //set up zone stimulation array 
        stimFuncs = new ArrayList<zoneStim>();
        stimFuncs.add(new kinZoneStim());
        stimFuncs.add(new dynZoneStim());
        stimFuncs.add(new jumpZoneStim());
        setCurZoneStim(0);
    }//ctor
    
    //build array of matched idxs in zone array so that we modify appropriate pairs/sets of points at same time
    protected int[][] buildMultiZoneAra(int[] _zIDXs, int numGrps){
        int numPerGrp = _zIDXs.length/numGrps;
        int[][] res = new int[numGrps][];
        int idx = 0;
        for(int grp=0;grp<numGrps;++grp) {
            int[] tmpGroup = new int[numPerGrp];
            for(int i=0; i<tmpGroup.length;++i) {tmpGroup[i]=_zIDXs[idx++];}
            res[grp]=tmpGroup;
        }
        return res;
    }//buildMultiZoneAra
    
    //set zone stim func type - 0==kinematic, 1==dynamic, 2== ? etc.
    public void setCurZoneStim(int _typ) {
        if((_typ <0) || (stimFuncs.size() <= _typ)) {pa.outStr2Scr("myZoneMod : non-existant zone stim selected : " + _typ); return;}
        curStimIdx = _typ;
        curStimFunc = stimFuncs.get(curStimIdx);
    }//setCurZoneStim
    
    //call every frame to modify zone - each zone will manage whether it is actually modified or not
    public void modZone(float _modAmt, boolean _beatDet, boolean _lastBeatDet) {        
        if (_beatDet) {                                                                //beat has been detected
            if(!_lastBeatDet) {                            startApplyMod(_modAmt);}    //rising edge of start of beat
            else {                                        applyMod(_modAmt);    }        //during beat
        } else if ((!_beatDet) && (_lastBeatDet)) {        endApplyMod();}                //trailing edge of beat
        //here is space between end of one beat and start of next
        //carry out spring/integration here on all zones if not fully kinematic
        if(0!= curStimIdx) {
            for(myZonePoint zp : zonePoints.values()) {    zp.fwdSimZoneNoStim();}
        }
    }//modZone            
//    //reset all points in zone and zone mod variables
//    public void resetZone() {
//        curBeat = 0;     curIdx = 0;
//        curIdxModVal = 1;        
//        for (myZonePoint zp : zonePoints.values()) {    zp.resetZone();}
//    }//resetZone
    
    //start the mod application cycle
    protected void startApplyMod(float _modAmt) {
        //any global beginning-of-the-beat modification code
        for(int grp=0;grp<zoneIDsPerGroup.length;++grp) {
//            pa.outStr2Scr("ZoneType : "+zoneType+"|grp:"+grp+"|curIdx : "+curIdx+"|zoneModCylTyp:"+zoneModCylTyp);
//            pa.outStr2Scr("\tzIdsPerGrp :"+zoneIDsPerGroup[grp][curIdx]+"|zonePtsSize:"+zonePoints.size());
            curStimFunc.startStimZone(zonePoints.get(zoneIDsPerGroup[grp][curIdx]), _modAmt);
        }        
    }    
    //apply modification to this zone
    protected void applyMod(float _modAmt) {
        for(int grp=0;grp<zoneIDsPerGroup.length;++grp) {
            curStimFunc.stimZone(zonePoints.get(zoneIDsPerGroup[grp][curIdx]), _modAmt);
            //zonePoints.get(zoneIDsPerGroup[grp][curIdx]).stimulateZoneForce(_modAmt);    
        }        
    }
    
    //end the mod application cycle, and cycle to next mod target if curBeat == beatsToCount
    protected void endApplyMod() {
        //finish up stim - any cleanup (like reset)
        for(int grp=0;grp<zoneIDsPerGroup.length;++grp) {
            curStimFunc.endStimZone(zonePoints.get(zoneIDsPerGroup[grp][curIdx]));
        }        
        //increment curbeat
        curBeat = (curBeat + 1) % beatsToCount;
        //check if cycled through
        if(curBeat == 0) {
            switch (zoneModCylTyp) {
            case 0 : {//increase only
                curIdx = (curIdx + 1) % maxSizeForCntr;    
                break;}
            case 1 : {//increase and decrease
                if(curIdx == maxSizeForCntr-1) {    curIdxModVal = -1;}//change direction if at size bounds
                else if (curIdx == 0) {                curIdxModVal = 1;}//change direction if at size bounds            
                curIdx = (curIdx + curIdxModVal);
                break;}
            case 2 :{//random
                curIdx = ThreadLocalRandom.current().nextInt(maxSizeForCntr);    //randomly pick another group to stimulate    
                break;}
            }            
        }
    }//endApplyMod


}//class myZoneMod

//classes to manage function calling zone stimulator - appropriate function call depending on what type of stimulation we have set
abstract class zoneStim{
    public zoneStim(){}
    //anything special to perform at beginning of beat 
    protected abstract void startStimZone(myZonePoint _zp, float _modAmt);
    //actual stimulation of zone
    protected abstract void stimZone(myZonePoint _zp,float _modAmt);
    //anything special to perform at ending of beat
    protected abstract void endStimZone(myZonePoint _zp);
    //anything to execute between active stimulations
    protected abstract void nonStimZone(myZonePoint _zp);
}//class zoneStim

class kinZoneStim extends zoneStim{
    public kinZoneStim() {    super();}
    @Override
    protected void startStimZone(myZonePoint _zp,float _modAmt) {//special setup to perform for this type of stimulation function
        _zp.resetZone();//reset last displacement
        _zp.stimulateZoneKin(_modAmt);
    }
    @Override
    protected void stimZone(myZonePoint _zp,float _modAmt) {
        _zp.resetZone();//reset last displacement
        _zp.stimulateZoneKin(_modAmt);
    }
    @Override
    protected void endStimZone(myZonePoint _zp) {}
    @Override
    protected void nonStimZone(myZonePoint _zp) {}
}//class kinZoneStim

class dynZoneStim extends zoneStim{
    public dynZoneStim() {    super();}
    @Override
    protected void startStimZone(myZonePoint _zp,float _modAmt) {//special setup to perform for this type of stimulation function
        _zp.stimulateZoneForceWithSpring(_modAmt);
        //stimulate zone springs
    }
    @Override
    protected void stimZone(myZonePoint _zp,float _modAmt) {
        _zp.stimulateZoneForceWithSpring(_modAmt);
        //stimulate zone springs
    }
    @Override
    protected void endStimZone(myZonePoint _zp) {_zp.fwdSimZoneNoStim();}
    @Override
    protected void nonStimZone(myZonePoint _zp) {_zp.fwdSimZoneNoStim();}
}//class dynZoneStim

class jumpZoneStim extends zoneStim{//starts with kinematic displacement, then continues with dynamic mass-spring
    public jumpZoneStim() {    super();}
    @Override
    protected void startStimZone(myZonePoint _zp,float _modAmt) {//special setup to perform for this type of stimulation function - kinematic displacement
        _zp.resetZone();//reset last displacement
        _zp.stimulateZoneKin(_modAmt);
        //stimulate zone springs
    }
    @Override
    protected void stimZone(myZonePoint _zp,float _modAmt) {
        _zp.stimulateZoneForceWithSpring(_modAmt);
        //stimulate zone springs
    }
    @Override
    protected void endStimZone(myZonePoint _zp) {_zp.fwdSimZoneNoStim();}
    @Override
    protected void nonStimZone(myZonePoint _zp) {_zp.fwdSimZoneNoStim();}
}//class jumpZoneStim
