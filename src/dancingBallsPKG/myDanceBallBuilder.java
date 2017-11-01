package dancingBallsPKG;

import java.util.*;
import java.util.concurrent.*;

import ddf.minim.analysis.FFT;

//builds ball in multiple threads
public class myDanceBallBuilder implements Callable<Boolean> {
	private myDancingBall d;
	private int mapIDX;
	private static float PI = (float) Math.PI, TWO_PI = PI*PI;
	private myZonePoint[] zonePts;

	public myDanceBallBuilder(myDancingBall _d, int _mapIDX) {
		d = _d;		
		mapIDX = _mapIDX;
		zonePts = d.zonePoints[mapIDX];
	}
	
	/**
	 * build map of zone neighborhoods for specific zone 
	 * @return map of per-zone point neighborhoods of myRndrdParts
	 */	
	private void buildMapAroundZonePoint(){
		int NBDSize = d.pa.max(d.verts.length/d.numZoneVerts[mapIDX],d.win.minVInNBD);
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


//Still slow but maybe we can build in multiple threads
class myDanceBallMapper implements Runnable{
	private myDancingBall d;
	private static float PI = (float) Math.PI, TWO_PI = PI*PI;

	public myDanceBallMapper(myDancingBall _d) {
		d = _d;		
	}
	
	private void buildFreqZoneMap() {
		List<myDanceBallBuilder> callBallBuilder = new ArrayList<myDanceBallBuilder>();
		List<Future<Boolean>> callBBFtrs = new ArrayList<Future<Boolean>>();		
		//want at least min # of verts per zone
		for(int i =0;i<d.numZones;++i) {
		//	int NBDSize = d.pa.max(d.verts.length/d.numZoneVerts[i],d.win.minVInNBD);
			callBallBuilder.add(new myDanceBallBuilder(d, i));
		}
		try {callBBFtrs = d.pa.th_exec.invokeAll(callBallBuilder);for(Future<Boolean> f: callBBFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }			
	}

	@Override
	public void run() {
		 buildFreqZoneMap();
		 //d.pa.outStr2Scr("Called run");
		 d.setFlags(d.isZoneMappedIDX, true);
		 d.finalInit();
	}
}


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
// 		} else {
//			for (float freq : allFreqsUsed.keySet()) {
//				Float[] tmpRes = new Float[sampSize];
//				float angleFreq = tpiInvF * freq;
//				for(int t=0;t<sampSize;++t) {	tmpRes[t] = (float) Math.cos(angleFreq * t);}	
//				resMap.put(freq, tmpRes);
//			} 			
// 		}		
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


class myDFTNoteMapper implements Callable<Boolean>{
	DancingBallWin win;
	myAudioManager mgr;

	float[][] pianoFreqsHarmonics, pianoMinFreqsHarmonics;
	
	float[][][] pianoSampleFreqs;
	float sampleRate,twoPiInvSamp;
	
	//hold all precalced trig for each sample rate possible, for each sample frequency derived
	//ConcurrentSkipListMap<Float, ConcurrentSkipListMap<Float, Float[]>> cosTblSample, sinTblSample;

	//precalced cos and sin of frequencies
	ConcurrentSkipListMap<Float, Float[]> cosTbl, sinTbl;
	int stKey, endKey;
	
	//current song buffer
	float[] buffer;
	//results for this mapper's range of notes - shared with other mappers in certain freq range
	ConcurrentSkipListMap<Float, Integer> lvlsPerKeyInRange;	
	//all frequencies used for trig precalc
	ConcurrentSkipListMap<Float, Integer> allFreqsUsed;
	
	//reference to owning map
	ConcurrentSkipListMap<Float, Integer> levelsPerPKeySingleCalc;
	
	//normalization value
	float normVal;
	
	//first call or not
	//boolean firstCall; myAudioManager mgr
	
	//arrays hold subset of keys this thread will execute
	public myDFTNoteMapper(myAudioManager _mgr,int _stIdx, int _endIdx ) {
		mgr=_mgr;
		stKey = _stIdx;
		endKey = _endIdx;
		int len=_endIdx - _stIdx + 1;
		pianoFreqsHarmonics = new float[len][];
		pianoMinFreqsHarmonics = new float[len+1][];
		//copy refs to array values (arrays of harmonics)
		System.arraycopy(mgr.dispPiano.pianoFreqsHarmonics, _stIdx, pianoFreqsHarmonics, 0, len);
		System.arraycopy(mgr.dispPiano.pianoMinFreqsHarmonics, _stIdx, pianoMinFreqsHarmonics, 0, len+1);
		int numSamplesPerKey = 10;
		//firstCall = true;
		allFreqsUsed = preCalcSamples(len, numSamplesPerKey);

	}//myDFTNoteMapper
	
	//arrays hold subset of keys this thread will execute
	public myDFTNoteMapper(DancingBallWin _win,int _stIdx, int _endIdx ) {
		win=_win;
		stKey = _stIdx;
		endKey = _endIdx;
		int len=_endIdx - _stIdx + 1;
		pianoFreqsHarmonics = new float[len][];
		pianoMinFreqsHarmonics = new float[len+1][];
		//copy refs to array values (arrays of harmonics)
		System.arraycopy(win.dispPiano.pianoFreqsHarmonics, _stIdx, pianoFreqsHarmonics, 0, len);
		System.arraycopy(win.dispPiano.pianoMinFreqsHarmonics, _stIdx, pianoMinFreqsHarmonics, 0, len+1);
		int numSamplesPerKey = 10;
		//firstCall = true;
		allFreqsUsed = preCalcSamples(len, numSamplesPerKey);

	}//myDFTNoteMapper
	
	//must be set before each dft run!
//	public void setPerRunValues(float _srte, float[] _buffer, 
//			ConcurrentSkipListMap<Float, Float[]> _cosTbl, 
//			ConcurrentSkipListMap<Float, Float[]> _sinTbl,
//			ConcurrentSkipListMap<Float, Integer> _lvlsPerKeyInRange		//destination
//			) {
	public void setPerRunValues(float _srte, float[] _buffer,
			ConcurrentSkipListMap<Float, Integer> _lvlsPerPKeySingleCalc,
			ConcurrentSkipListMap<Float, Integer> _lvlsPerKeyInRange		//destination
			) {
		sampleRate = _srte;
		buffer = _buffer;//float array of length samplesize
//		cosTbl = cosTblSample.get(sampleRate);
//		sinTbl = sinTblSample.get(sampleRate);		
//		cosTbl = _cosTbl;
//		sinTbl = _sinTbl;
		twoPiInvSamp = (float) (2.0 * Math.PI / sampleRate);
		levelsPerPKeySingleCalc = _lvlsPerPKeySingleCalc;
		lvlsPerKeyInRange = _lvlsPerKeyInRange;
	}
	
	private ConcurrentSkipListMap<Float, Integer> preCalcSamples(int len, int numSamplesPerKey) {
		normVal = numSamplesPerKey * numSamplesPerKey;
		//create numSamplesPerKey sampled frequencies to test audio at, for every key, to find average
		pianoSampleFreqs = new float[len][][];		
		ConcurrentSkipListMap<Float, Integer> res = new ConcurrentSkipListMap<Float, Integer>();
		for(int key=0;key<pianoFreqsHarmonics.length;++key) {//for each key
			float[] lowFreqHarmAra = pianoMinFreqsHarmonics[key], hiFreqHarmAra = pianoMinFreqsHarmonics[key+1];
			float[][] perKeySamples = new float[lowFreqHarmAra.length][];
			for(int h=0;h<lowFreqHarmAra.length;++h) {//for each harmonic of key ->0 idx is fundamental
				float[] harmSamples = new float[numSamplesPerKey];
				for(int s=0;s<numSamplesPerKey;++s) {//for each desired sample
					float freq  = (float) ThreadLocalRandom.current().nextDouble(lowFreqHarmAra[h],hiFreqHarmAra[h]);
					harmSamples[s] = freq;
					//dummy variable for field - using key as ordered set to remove dupes
					res.put(freq, 1);					
				}
				perKeySamples[h] = harmSamples;
			}
			pianoSampleFreqs[key]=perKeySamples;			
		}//preCalcSamplesAndTrig		
		return res;
	}
	
	/**
	 * calculate the individual level manually using a sample of the signal as f(t)
	 */	
	public void calcIndivFreqLevelOnSamples() {		
		float cosSum = 0, sinSum = 0, A;
		for(int key=0;key<pianoFreqsHarmonics.length;++key) {//for every key being compared
			cosSum =0;sinSum=0;A=0;
			float[] harmSamples = pianoSampleFreqs[key][0];//fundamental only for now
			for(float harm  : harmSamples) {
				for (int t=0;t<buffer.length; ++t) {		//for every sample
					cosSum += buffer[t] * cosTbl.get(harm)[t];
					sinSum += buffer[t] * sinTbl.get(harm)[t];
				}
			}			
			A = ((cosSum * cosSum) + (sinSum * sinSum))/normVal;//A[n] = sqrt (c(f)^2 + s(f)^2)
			levelsPerPKeySingleCalc.put(A, key+stKey);				
			lvlsPerKeyInRange.put(A, key+stKey);	
		}
	}//calcIndivFreqLevelOnSamples
	
	
	/**
	 * calculate the individual level manually using a sample of the signal as f(t), not using precalced frequencies
	 * TRIG IS FASTER THAN PRECALC
	 */	
	public void calcIndivFreqLevelNoPreCalcOnSamples() {
		//current buffer of song playing
		float cosSum = 0, sinSum = 0, A;		
		for(int key=0;key<pianoFreqsHarmonics.length;++key) {//for every key being compared
			cosSum =0;sinSum=0;A=0;
			float[] harmSamples = pianoSampleFreqs[key][0];//fundamental only for now
			for(float harm  : harmSamples) {
				float tpHarm = harm *  twoPiInvSamp;
				for (int t=0;t<buffer.length; ++t) {		//for every sample
					float tpHarmT = t*tpHarm;
					cosSum += buffer[t] * (float)(Math.cos(tpHarmT));
					sinSum += buffer[t] * (float)(Math.sin(tpHarmT));
				}	           
			}
			A = ((cosSum * cosSum) + (sinSum * sinSum))/normVal; //A[n] = sqrt (c(f)^2 + s(f)^2)
			levelsPerPKeySingleCalc.put(A, key+stKey);				
			lvlsPerKeyInRange.put(A, key+stKey);	
		}
	}//calcIndivFreqLevelNoPreCalcOnSamples
	
	@Override
	public Boolean call() throws Exception {
//		if (cosTbl == null) {
//			calcIndivFreqLevelNoPreCalc();
//		} else {
//			calcIndivFreqLevel();				
//		}	
//		if (firstCall) {
//			preCalcTrig();
//			allFreqsUsed = null;
//			firstCall = false;
//		} else {
//			if (cosTbl == null) {//would be null if no precalculated cos/sin tables existed for current song's sample rate
//				//System.out.println("no precalc");
		//trig is faster than precalc - precalc suffers from stutters initially, until cache is loaded for each thread (?)
		calcIndivFreqLevelNoPreCalcOnSamples();
//			} else {
//				calcIndivFreqLevelOnSamples();				
//			}	
//		}
		return true;
	}
	

}//myDFTNoteMapper
