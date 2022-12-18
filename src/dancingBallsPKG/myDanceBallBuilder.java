package dancingBallsPKG;

import java.util.*;
import java.util.concurrent.*;


//builds ball in multiple threads
//Still slow but maybe we can build in multiple threads
public class myDanceBallBuilder implements Runnable{
	//needs to be rebuilt if numzones should change
	private myDancingBall d;
	List<myDanceBallBuilder_Callable> callBallBuilder;
	List<Future<Boolean>> callBBFtrs;	

	public myDanceBallBuilder(myDancingBall _d) {
		d = _d;		
		callBallBuilder = new ArrayList<myDanceBallBuilder_Callable>();
		callBBFtrs = new ArrayList<Future<Boolean>>();	
		for(int i =0;i<d.numZones;++i) {
		//	int NBDSize = d.pa.max(d.verts.length/d.numZoneVerts[i],d.win.minVInNBD);
			callBallBuilder.add(new myDanceBallBuilder_Callable(d, i));
		}
	}

	@Override
	public void run() {
		 try {callBBFtrs = d.pa.th_exec.invokeAll(callBallBuilder);for(Future<Boolean> f: callBBFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }
		 d.setFlags(d.isZoneMappedIDX, true);
		 d.finalInit();
	}
}

class myDanceBallBuilder_Callable implements Callable<Boolean> {
	private myDancingBall d;
	private int mapIDX;
	private static float PI = (float) Math.PI, TWO_PI = PI*PI;
	private myZonePoint[] zonePts;

	public myDanceBallBuilder_Callable(myDancingBall _d, int _mapIDX) {
		d = _d;		
		mapIDX = _mapIDX;
		zonePts = d.zonePoints[mapIDX];
	}
	
	private int _max(int a, int b) {return (a > b ? a : b);}	
	/**
	 * build map of zone neighborhoods for specific zone 
	 * @return map of per-zone point neighborhoods of myRndrdParts
	 */	
	private void buildMapAroundZonePoint(){
		int NBDSize = _max((int)(d.verts.length/(1.0*d.numZoneVerts[mapIDX])),d.win.minVInNBD);
		Float dist;
		//TODO redo this more efficiently
		for(Integer i=0;i<zonePts.length;++i) {
			ConcurrentNavigableMap<Float, myRndrdPart> tmpMap = new ConcurrentSkipListMap<Float, myRndrdPart>();
			for(int j=0; j<d.verts.length; ++j) {
				myRndrdPart tmpPart = d.verts[j];
				//purturb distance a small amount to minimize likelihood of collisions - precalced to speed up calculation
				float del = d.zonePtsDel[j%d.zonePtsDel.length];
				//dist = (myPointf._dist(zonePts[i], d.verts[j].aPosition[d.verts[j].curIDX]) * del);
				dist = (myPointf._dist(zonePts[i].pt, tmpPart.aPosition[tmpPart.curIDX]) * del);
				tmpMap.put(dist,tmpPart);
			}
			Float[] keys = tmpMap.keySet().toArray(new Float[0]);
			zonePts[i].setNeighborhood(tmpMap.subMap(keys[0], keys[NBDSize]));
		}		
	}//buildMapAroundZonePoint

	@Override
	public Boolean call() throws Exception {
		buildMapAroundZonePoint(); 		
		return true;
	}

}//myDanceBallBuilder

//class myTrigPreCBuilder implements Callable<Boolean>{
//	ConcurrentSkipListMap<Float, Integer> allFreqsUsed;
//	DancingBallWin win;
//	float tpiInvF, sampleRate;
//	int sampleSize;
//	
//	public myTrigPreCBuilder(DancingBallWin _win, float _smplRate, ConcurrentSkipListMap<Float, Integer> _allFreqsUsed) {
//		win=_win;
//		allFreqsUsed = _allFreqsUsed;
//		sampleRate = _smplRate;
//		tpiInvF =  win.pa.TWO_PI/sampleRate;
//		sampleSize = win.songBufSize;		
//	}
//	
//	//build map of either sin or cosine based precalcs of 2pi*freq*t/Fs where Fs is sample rate,
//	//calculation relies on sample rate for tpiInvF == 2PI / sampleRate
//	private ConcurrentSkipListMap<Float, Float[]> calcTrigMap(int sampSize, boolean isSine){
//		ConcurrentSkipListMap<Float, Float[]> resMap = new ConcurrentSkipListMap<Float, Float[]>();
//		if(isSine) {
//			for (float freq : allFreqsUsed.keySet()) {
//				Float[] tmpRes = new Float[sampSize];
//				float angleFreq = tpiInvF * freq;
//				for(int t=0;t<sampSize;++t) {	tmpRes[t] = (float) Math.sin(angleFreq * t);}	
//				resMap.put(freq, tmpRes);
//			}
//		} else {
//			for (float freq : allFreqsUsed.keySet()) {
//				Float[] tmpRes = new Float[sampSize];
//				float angleFreq = tpiInvF * freq;
//				for(int t=0;t<sampSize;++t) {	tmpRes[t] = (float) Math.cos(angleFreq * t);}	
//				resMap.put(freq, tmpRes);
//			} 			
//		}		
//		return resMap;
//	}//calcTrigMap	
//
//	@Override
//	public Boolean call() throws Exception {
//		win.sinTbl.put(sampleRate, calcTrigMap( sampleSize, true ));
//		win.cosTbl.put(sampleRate, calcTrigMap( sampleSize, false));
//		// TODO Auto-generated method stub
//		return true;
//	}
//}
//
////precalculate trig functions in separate threads to speed up computation
//class myTrigPrecalc implements Runnable{
//	DancingBallWin win;
//	ConcurrentSkipListMap<Float, Integer> allFreqsUsed;
//	
//	public myTrigPrecalc(DancingBallWin _win, ConcurrentSkipListMap<Float, Integer> _allFreqsUsed) {
//		win = _win; allFreqsUsed = _allFreqsUsed;
//		win.cosTbl = new ConcurrentSkipListMap<Float,ConcurrentSkipListMap<Float, Float[]>>();
//		win.sinTbl = new ConcurrentSkipListMap<Float,ConcurrentSkipListMap<Float, Float[]>>();
//	}
//	//build thread calls to build trig precalc
//	private void buildAllTrigPrecalc() {		
//		List<myTrigPreCBuilder> callPreCalcs = new ArrayList<myTrigPreCBuilder>();
//		List<Future<Boolean>> callPreCalcFtrs = new ArrayList<Future<Boolean>>();			
//		//want at least min # of verts per zone
//		//for(int i =0;i<win.sampleRates.size();++i) {			callPreCalcs.add(new myTrigPreCBuilder(win,win.sampleRates.get[i], allFreqsUsed));		}
//		for(float sampleRate : win.sampleRates.keySet()) {	
//			callPreCalcs.add(new myTrigPreCBuilder(win,sampleRate, allFreqsUsed));		
//		}
//		try {callPreCalcFtrs = win.pa.th_exec.invokeAll(callPreCalcs);for(Future<Boolean> f: callPreCalcFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }			
//	}//buildAllTrigPrecalc
//	
//	
//	@Override
//	public void run() {
//		buildAllTrigPrecalc();	
//		win.pa.outStr2Scr("done with trig precalc");
//		//build analyzer once this process is complete
//		win.initDFTAnalysisThrds(10);//10 threads
//	}
//	
//}//myTrigPrecalc
