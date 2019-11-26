package dancingBallsPKG;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentSkipListMap;

public class myDFTNoteMapper implements Callable<Boolean>{
	DancingBallWin win;
	static int count=0;
	int ID;
	myAudioManager mgr;
	//how much to multiply a low freq to go to the next larger frequency to cover the equispaced sampling for each note span
	final float augNSth;
	float[][] pianoFreqsHarmonics, pianoMinFreqsHarmonics;
	float[][] eqTempFreqsHarms, eqTempMinFreqsHarms;
	
	float[][] freqHarmsToUse, freqMinHarmsToUse;
	//per key, per harmonic multiple, per sample 
	float[][][] pianoSampleFreqs;
	float[][][] eqSampleFreqs;
	float[][][] exampleFreqsToUse;
	float sampleRate,twoPiInvSamp;
	int numValsToProcess;
	
	//hold all precalced trig for each sample rate possible, for each sample frequency derived
	//ConcurrentSkipListMap<Float, ConcurrentSkipListMap<Float, Float[]>> cosTblSample, sinTblSample;
	//precalced cos and sin of frequencies
	//ConcurrentSkipListMap<Float, Float[]> cosTbl, sinTbl;
	//start and ending idxs of piano keys this mapper will calculate
	int stKey, endKey;
	
	//current song buffer
	float[] buffer;
	//results for this mapper's range of notes - indiv threads 
	ConcurrentSkipListMap<Float, Integer> lvlsPerKeyInRange;		
	//reference to owning map; shared among a subset of threads
	ConcurrentSkipListMap<Float, Integer> lvlsPerPKey, lvlsPerPKeyShared;
	
	//reference to owning map holding note(as -key) to levels(value)
	//array of values is the past n values, plus one level for average over past n values, and last index is current level
	int curKeylvlIdx;//current index
	ConcurrentSkipListMap<Integer, Float[]> perPKeyLvls;
	//which function to use to calculate levels - set when song changed
	int funcToUse = 0;
	//arrays hold subset of key freqs this thread will execute
	public myDFTNoteMapper(myAudioManager _mgr,int _stIdx, int _endIdx ) {
		mgr=_mgr;
		ID = count++;
		stKey = _stIdx;
		endKey = _endIdx;
		numValsToProcess=_endIdx - _stIdx + 1;
		buffer = new float[1024];
		pianoFreqsHarmonics = new float[numValsToProcess][];
		pianoMinFreqsHarmonics = new float[numValsToProcess+1][];
		eqTempFreqsHarms = new float[numValsToProcess][];
		eqTempMinFreqsHarms = new float[numValsToProcess+1][];
		//copy refs to array values (arrays of harmonics)
		System.arraycopy(mgr.dispPiano.pianoFreqsHarmonics, _stIdx, pianoFreqsHarmonics, 0, numValsToProcess);
		System.arraycopy(mgr.dispPiano.pianoMinFreqsHarmonics, _stIdx, pianoMinFreqsHarmonics, 0, numValsToProcess+1);
		System.arraycopy(mgr.dispPiano.eqTempFreqsHarms, _stIdx, eqTempFreqsHarms, 0, numValsToProcess);
		System.arraycopy(mgr.dispPiano.eqTempMinFreqsHarms, _stIdx, eqTempMinFreqsHarms, 0, numValsToProcess+1);
		int numSamplesPerKey = 5;
		augNSth = (float) Math.exp(Math.log(2.0)/(12.0f * numSamplesPerKey));
		//bufIncr = stKey < 30 ? 1 : 2;

		//normVal = numSamplesPerKey * numSamplesPerKey/(1.0f * bufIncr * bufIncr);
		//allFreqsUsed = preCalcRandSamples(len, numSamplesPerKey);
		//for each key to process, for each harmonic multiple (fund==idx 0), for each sample
		pianoSampleFreqs = new float[numValsToProcess][][];		
		eqSampleFreqs = new float[numValsToProcess][][];		

		//allFreqsUsed = 
		preCalcRandUniSamples(numValsToProcess, numSamplesPerKey, pianoSampleFreqs, pianoFreqsHarmonics, pianoMinFreqsHarmonics);
		preCalcRandUniSamples(numValsToProcess, numSamplesPerKey, eqSampleFreqs,eqTempFreqsHarms, eqTempMinFreqsHarms);

	}//myDFTNoteMapper

	//build a map of uniformly spaced samples between min and max freq for a key
	private void preCalcRandUniSamples(int len, int numExamplesPerKey, float[][][] sampleFreqs, float[][] harmFreqs, float[][] minHarmFreqs){
		//create numSamplesPerKey sampled frequencies to test audio at, for every key, to find average
		int numHarmonics = minHarmFreqs[0].length;
		float[]fundFreqAra;
		//array of harmonic # (0 is fund) of samples (idx 0 is array of all fundamentals)
		float[][] perKeyExamples;
		for(int key=0;key<harmFreqs.length;++key) {//for each key			
			perKeyExamples = new float[numHarmonics][];
			fundFreqAra = new float[numExamplesPerKey];
			//first calc fundamentals : numExamplesPerKey equally spaced freqs between 
			//lowFreqHarmAra[0] and highFreqHarmAra[0]
			float stFreq = minHarmFreqs[key][0];//fundamental of lowest frequency of this key
			for(int ex=0;ex<numExamplesPerKey;++ex) {//get numExamplesPerKey equally spaced fundamentals
				fundFreqAra[ex] = stFreq;
				stFreq *= augNSth;
			}
			perKeyExamples[0]= fundFreqAra;
			//now calculate multiples of all harmonics for arrays to put in perKeyExamples
			for(int h=1;h<numHarmonics;++h) {//for harmonic role (fundamental, 2nd harmonic, etc)
				float[] harmSamples = new float[numExamplesPerKey];
				float mult = h+1;
				for(int ex=0;ex<numExamplesPerKey;++ex) {harmSamples[ex]=mult*fundFreqAra[ex];}	//for every example
				perKeyExamples[h] = harmSamples;
			}
			sampleFreqs[key]=perKeyExamples;			
		}//preCalcSamplesAndTrig		
	}	
	
	//set values relevant to each song, when they change
	//bufMult is how big the buffer should be - only needs to be big enough for 1 
	//sample for high frequency ranges, but lower ranges perform better with a bigger buffer
	public void setPerSongValues(float _srte, int _smplBufSize, int _bufMult) {
		//if((ID == 0) || (ID == 7) ){System.out.println("setPerSongValues in ID:" + ID + " buff mult : " + _bufMult + " buffer size :"+(_bufMult * _smplBufSize));}
		sampleRate = _srte;
		twoPiInvSamp = (float) (2.0 * Math.PI / sampleRate);
		buffer = new float[_bufMult * _smplBufSize];		
	}
	
	//must be set before each dft run
	public void setPerRunValues(float[] _buffer,int _curProcIdx,boolean _usePianoTune, //boolean debug,
			ConcurrentSkipListMap<Float, Integer> _lvlsPerPKey,
			ConcurrentSkipListMap<Integer, Float[]> _perPKeyLvls,
			ConcurrentSkipListMap<Float, Integer> _lvlsPerKeyInRange,		//destination
			int _funcToUse) {
		//sampleRate = _srte;
		if(_usePianoTune) {
			freqHarmsToUse = pianoFreqsHarmonics;
			exampleFreqsToUse = pianoSampleFreqs;	
		} else {
			freqHarmsToUse = eqTempFreqsHarms;
			exampleFreqsToUse = eqSampleFreqs;
		}
		funcToUse = _funcToUse;
		//if(ID == 0) {System.out.println("setPerRunValues in ID:" + ID + " : len of  exampleFreqsToUse : " + exampleFreqsToUse.length + " _usePianoTune : " + _usePianoTune);}
		int keptLen = buffer.length-_buffer.length;
		//if(debug) {if((ID == 0) || (ID == 7) ){System.out.println("setPerRunValues in ID:" + ID + " : size of dft lcl buffer : " + buffer.length  + " keptLen : " + keptLen + " _bufferLen : " + _buffer.length);}}
		curKeylvlIdx = _curProcIdx;
		//buffer here is treated like a stack - first move old values over 1 "window" of _buffer.length size
		if(keptLen > 0) {System.arraycopy(buffer, _buffer.length, buffer, 0, keptLen);}
		//now copy new sample to buffer at end of buffer
		System.arraycopy(_buffer, 0, buffer, keptLen, _buffer.length);
		//buffer = _buffer;//float array of length samplesize
		lvlsPerPKey = _lvlsPerPKey;
		perPKeyLvls = _perPKeyLvls;
		lvlsPerKeyInRange = _lvlsPerKeyInRange;
	}	
	
	public void setPerRunSharedMap(ConcurrentSkipListMap<Float, Integer> _lvlsPerKeyShared) {
		lvlsPerPKeyShared = _lvlsPerKeyShared;
	}
	
	private void setValues(int absKey, double cosSum, double sinSum, float freq) {
		//float A = (float) Math.sqrt(freq *((cosSum * cosSum) + (sinSum * sinSum)));///normVal;// (float) Math.sqrt(((cosSum * cosSum) + (sinSum * sinSum))/normVal);
		float A =  (float) Math.sqrt( ((cosSum * cosSum) + (sinSum * sinSum)));///normVal;// (float) Math.sqrt(((cosSum * cosSum) + (sinSum * sinSum))/normVal);
		lvlsPerPKey.put(A, absKey);	
		//perPKeyLvls.put(absKey, A);
		lvlsPerKeyInRange.put(A, absKey);	
		lvlsPerPKeyShared.put(A, absKey);
		
		Float[] tmp = perPKeyLvls.get(absKey);
		tmp[tmp.length-1] = A;
		//Float divVal = (tmp.length-2.0f), oldVal = tmp[curKeylvlIdx]/divVal;
		Float oldVal = tmp[curKeylvlIdx];
		tmp[curKeylvlIdx] = A;
		tmp[tmp.length-2] = tmp[tmp.length-2] - oldVal + A; 
	}//setValues
	
	//only fundamental frequencies
	public void calcOneSampleFreqLevelAllHarms() {//single sample of fundamental - actual key center freq
		//current buffer of song playing
		double cosSum = 0, sinSum = 0;		
		for(int key=0;key<numValsToProcess;++key) {//for every key being compared
			cosSum =0;sinSum=0;
			float[] harmonics = freqHarmsToUse[key];
			//scale by fundamental result, so lower freqs don't benefit from higher freqs
			float tpHarm = harmonics[0] *  twoPiInvSamp;		
			for (int t=0;t<buffer.length;  ++t) {		//for every sample
				float tpHarmT = t*tpHarm;
				cosSum += buffer[t] * (Math.cos(tpHarmT));
				sinSum += buffer[t] * (Math.sin(tpHarmT));
			}	
			double fundScaleVal =  Math.sqrt((cosSum * cosSum) + (sinSum * sinSum));
			cosSum =0;sinSum=0;
			for(int h=0; h< harmonics.length; ++h) {
				tpHarm = harmonics[h] *  twoPiInvSamp;				
				for (int t=0;t<buffer.length;  ++t) {		//for every sample
					float tpHarmT = t*tpHarm;
					cosSum += buffer[t] * (Math.cos(tpHarmT));
					sinSum += buffer[t] * (Math.sin(tpHarmT));
				}	
			}
			setValues(key+stKey, fundScaleVal* cosSum, fundScaleVal* sinSum, harmonics[0]);//fundamental as scaling multiplier
		}		
	}//calcIndivFreqLevelNoPreCalcOnSamples
	
	
	//only fundamental frequencies
	public void calcOneSampleFundFreqLevel() {//single sample of fundamental - actual key center freq
		//current buffer of song playing
		double cosSum = 0, sinSum = 0;		
		for(int key=0;key<numValsToProcess;++key) {//for every key being compared
			cosSum =0;sinSum=0;
			float harm = freqHarmsToUse[key][0];//fundamental only for now
			//for(float harm  : harmSamples) {
			float tpHarm = harm *  twoPiInvSamp;
			for (int t=0;t<buffer.length;  ++t) {		//for every sample
				float tpHarmT = t*tpHarm;
				cosSum += buffer[t] * (Math.cos(tpHarmT));
				sinSum += buffer[t] * (Math.sin(tpHarmT));
			}			
			setValues(key+stKey, cosSum,  sinSum, harm);
		}		
	}//calcOneSampleFundFreqLevel
	
	
	/**
	 * calculate the individual level manually using a sample of the signal as f(t), not using precalced frequencies
	 * TRIG IS FASTER THAN PRECALC.  which is odd.
	 */	
	public void calcAllSmplFundFreqLevel() {//multiple samples of fundamental
		//current buffer of song playing
		double cosSum = 0, sinSum = 0;		
		for(int key=0;key<numValsToProcess;++key) {//for every key being compared
			cosSum =0;sinSum=0;
			float[] harmSamples = exampleFreqsToUse[key][0];//fundamental only for now - all samples' fundamental freq
			for(float harm  : harmSamples) {			//for each sample
				float tpHarm = harm *  twoPiInvSamp;
				for (int t=0;t<buffer.length; ++t) {		//for every sample
					float tpHarmT = t*tpHarm;
					cosSum += buffer[t] * (Math.cos(tpHarmT));
					sinSum += buffer[t] * (Math.sin(tpHarmT));
				}	           
			}
			setValues(key+stKey,  cosSum/harmSamples.length,  sinSum/harmSamples.length, harmSamples[harmSamples.length/2]);
		}
	}//calcAllSmplFundFreqLevel
	
	@Override
	public Boolean call() throws Exception {
		//System.out.println("ID : " + ID + " func to use : " + funcToUse);
		switch(funcToUse) {
			case 0 : {calcOneSampleFreqLevelAllHarms(); break;}//1 sample, all harmonics
			case 1 : {calcOneSampleFundFreqLevel(); break;}//1 sample, fundamental only
			case 2 : {calcAllSmplFundFreqLevel(); break;}//all samples, fundamental only
			default : {calcOneSampleFundFreqLevel(); break;}
		}
		//calcOneSampleFreqLevelAllHarms();
		//calcOneSampleFundFreqLevel();
		//calcAllSmplFundFreqLevel();
		return true;
	}
	

}//myDFTNoteMapper