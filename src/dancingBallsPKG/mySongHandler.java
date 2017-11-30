package dancingBallsPKG;

import java.util.concurrent.ConcurrentSkipListMap;

import ddf.minim.*;
import ddf.minim.analysis.*;
import ddf.minim.ugens.*;

//to handle midi communication
import javax.sound.midi.*;

public abstract class mySongHandler {
	protected Minim minim;
	public String fileName, dispName;
	//max level seen so far - idx 0 is dft, idx1 is fft
	public float[] barDispMaxLvl;
	//length in millis, minimum frequency in fft, # of zones to map energy to
	public int songLength, minFreqBand, numZones;
	//start frequencies, middle frequencies, and end frequencies for each of numbands bands
	protected float[] stFreqs, endFreqs, midFreqs;
	//for audio recordings, otherwise set to 0.
	public float sampleRate;
	protected int songBufSize;		//must be pwr of 2
	//beat detection stuff
	protected int sensitivity, insertAt;
	protected boolean[] fIsOnset;
	protected float[][] feBuffer, fdBuffer;
	protected long[] fTimer;

	public mySongHandler(Minim _minim, String _fname, String _dname, int _sbufSize) {
		minim=_minim; fileName=_fname; dispName=_dname;songBufSize=_sbufSize;
		barDispMaxLvl = new float[2];		
		barDispMaxLvl[0]=.01f;barDispMaxLvl[1]=.01f;
		sampleRate=0;
	}
	
	public void setForwardVals(WindowFunction win, int fftMinBandwidth, int _numZones) {
		numZones = _numZones;//number of zones for dancing ball (6)
		minFreqBand = fftMinBandwidth;//minimum frequency to consider (== minimum bandwidth fft is required to handle)
		setupFreqAras();
		barDispMaxLvl = new float[2];		barDispMaxLvl[0]=.01f;barDispMaxLvl[1]=.01f;	
		//individual  handling for each type of song handler (midi or mp3)
		setForwardValsIndiv(win);
	}//setForwardVals
	
	//set up precomputed frequency arrays for each zone (6)
	protected void setupFreqAras() {
		stFreqs = new float[numZones];	endFreqs = new float[numZones];	midFreqs = new float[numZones];
		//stFreqIDX = new int[numZones]; endFreqIDX = new int[numZones];
		float maxFreqMult = 9.9f;//max multiplier to minFreqBand to consider
		//multiplier for frequencies to span minFreqBand * ( 1.. maxFreqMult) ==> 2 ^ maxFreqMult/numZones
		float perFreqMult = (float) Math.pow(2.0, maxFreqMult/numZones);
		stFreqs[0] = minFreqBand;
		for(int i=0; i<numZones;++i) {
			endFreqs[i] = stFreqs[i] * perFreqMult;
//			stFreqIDX[i] = fft.freqToIndex(stFreqs[i]);
//			endFreqIDX[i] = fft.freqToIndex(endFreqs[i]);
			if(i<numZones-1) {//set up next one
				stFreqs[i+1] = endFreqs[i]; 
			}
			midFreqs[i]= .5f *( stFreqs[i] + endFreqs[i]);
		}
		setupFreqArasIndiv();
	}//setupFreqAras
	
	protected void initBeatDet() {
		//these are used for beat detection in each zone
		fIsOnset = new boolean[numZones];		
		int tmpAraSiz = (int) (sampleRate / songBufSize);
		feBuffer = new float[numZones][tmpAraSiz];
		fdBuffer = new float[numZones][tmpAraSiz];
		fTimer = new long[numZones];
		long start = System.currentTimeMillis();
		for (int i = 0; i < fTimer.length; i++)	{
			fTimer[i] = start;
		}
	}//initBeatDet
	
	//set max level for this song seen so far from dft analysis
	public void setDFTMaxLvl(float maxLvl) {barDispMaxLvl[0] = (barDispMaxLvl[0] < maxLvl ? maxLvl : barDispMaxLvl[0]);	}
	
	////////////////////////////////
	//beat detect based on minim library implementation-detect beats in each predefined zone
	public void beatDetect(float[] avgs) {
		float instant, E, V, C, diff, dAvg, diff2;
		long now = System.currentTimeMillis();
		for (int i = 0; i < numZones; ++i){
			instant = avgs[i];					//level averages at each zone - precalced from fft
			E = calcMean(feBuffer[i]);
			V = calcVar(feBuffer[i], E);
			C = (-0.0025714f * V) + 1.5142857f;
			diff = (float)Math.max(instant - C * E, 0);
			dAvg = specAverage(fdBuffer[i]);
			diff2 = (float)Math.max(diff - dAvg, 0);
			if (now - fTimer[i] < sensitivity){
				fIsOnset[i] = false;
			}else if (diff2 > 0){
				fIsOnset[i] = true;
				fTimer[i] = now;
			}else{
				fIsOnset[i] = false;
			}
			feBuffer[i][insertAt] = instant;
			fdBuffer[i][insertAt] = diff;
		}
		insertAt++;
		if (insertAt == feBuffer[0].length)	{
			insertAt = 0;
		}
	}//
	
	//check this to see if beat has been detected
	public boolean[] beatDetectZones() {
		boolean[] retVal = new boolean[fIsOnset.length];
		for(int i=0;i<fIsOnset.length;++i) {retVal[i]=fIsOnset[i];}
		return retVal;
	}//beatDetectZones	
	private float calcMean(float[] ara){float avg=ara[0];for (int i=1; i<ara.length; ++i){avg += ara[i];}avg /= ara.length;return avg;}
	private float specAverage(float[] ara){float avg = 0,num = 0;for (int i=1; i<ara.length; ++i){	if (ara[i]>0)	{avg += ara[i];++num;}	}if (num > 0){avg /= num;}return avg;}
	private float calcVar(float[] ara, float val){float V = (float)Math.pow(ara[0] - val, 2);for(int i=1; i<ara.length; ++i){V += (float)Math.pow(ara[i] - val, 2);} V/=ara.length;return V;}		
	
	//grab next set of samples/data to analyze for audio
	protected abstract void stepAudio();
	//individual handling of setup function
	protected abstract void setupFreqArasIndiv();
	protected abstract void setForwardValsIndiv(WindowFunction win);
	
	public abstract boolean isPlaying();
	//song control
	public abstract void play();
	public abstract void play(int millis);
	public abstract void pause();
	public abstract void rewind();
	//get array of samples to analyze
	public abstract float[] mixBuffer();
	//change current playing location
	public abstract void modPlayLoc(float modAmt);
	
	public abstract boolean donePlaying();
	//get position in current song
	public abstract int getPlayPos();
	
	//analysis results
	public abstract float[][] getFwdBandsFromAudio();
	public abstract float[][] getFwdZoneBandsFromAudio();
	public abstract void getFwdFreqLevelsInHarmonicBands(float[][] keyMinAra, ConcurrentSkipListMap<Float, Integer> res1, ConcurrentSkipListMap<Integer, Float[]> res2, int curIdx);
	
}//mySongHandler


//handles an mp3 song, along with transport control
class myMP3SongHandler extends mySongHandler{	
	private AudioPlayer playMe;	
	
	public FFT fft;
	//public static final float[] FreqCtrs = new float[] {};//10 bands : 22Hz to 22KHz
	public int[] stFreqIDX, endFreqIDX;
		
	/**
	 * build song handler
	 * @param _minim ref to minim library object, to load song
	 * @param _fname song name
	 * @param _dname name to display
	 * @param _sbufSize song buffer size
	 */
	public myMP3SongHandler(Minim _minim, String _fname, String _dname, int _sbufSize) {
		super(_minim,_fname, _dname, _sbufSize);
		playMe = minim.loadFile(fileName, songBufSize);
		songLength = playMe.length();
		fft = new FFT(playMe.bufferSize(), playMe.sampleRate() );
		insertAt = 0;
		sensitivity = 10;	
		sampleRate = playMe.sampleRate();
		//System.out.println("Song: " + dispName + " sample rate : " + playMe.sampleRate());
	}
	
	//set values required for fft calcs.	
	@Override
	protected void setForwardValsIndiv(WindowFunction win) {
		//set fft windowing function
		fft.window(win);
		//set fft to not calculate any averages
		fft.noAverages();
	}//setFFTVals
	
	@Override
	protected void setupFreqArasIndiv() {
		stFreqIDX = new int[numZones]; endFreqIDX = new int[numZones];
		float maxFreqMult = 9.9f;//max multiplier to minFreqBand to consider
		//multiplier for frequencies to span minFreqBand * ( 1.. maxFreqMult) ==> 2 ^ maxFreqMult/numZones
		for(int i=0; i<numZones;++i) {
			stFreqIDX[i] = fft.freqToIndex(stFreqs[i]);
			endFreqIDX[i] = fft.freqToIndex(endFreqs[i]);
		}
	}//setupFreqAras

//	//go through entire song, find ideal center frequencies and ranges for each of numZones zone based on energy content
//	//split frequency spectrum into 3 areas ("bass", "mid" and "treble"), find numzones/3 ctr frequencies in each of 3 bands
//	//# zones should be %3 == 0
//	//this should be saved to disk so not repeatedly performed
//	private void setZoneFreqs() {
//		//first analyze all levels in entire song
//		//next group frequencies
//	}
	
	//call every frame before any fft analysis - steps fft forward over a single batch of samples
	@Override
	public void stepAudio() {fft.forward( playMe.mix ); }
	
	//get levels of all bands in spectrum
	@Override
	public float[][] getFwdBandsFromAudio() {
		int specSize = fft.specSize();	//should be songBufSize / 2 + 1 : approx 512 bands
		float[] bandRes = new float[specSize], bandFreq = new float[specSize];
		for(int i=0;i<specSize;++i) {	
			bandFreq[i] = fft.indexToFreq(i);
			bandRes[i] = fft.getBand(i);
		}
		return new float[][] {bandRes,bandFreq};
	}//fftFwdBandsFromAudio	

	//returns all spectrum results averaged into numZones bands
	//only use numBands divs of first specSize/2 frequencies
	@Override
	public float[][] getFwdZoneBandsFromAudio() {
		float[] bandRes = new float[numZones], bandFreq = new float[numZones];
		for(int i=0; i<numZones;++i) {
			bandRes[i] = fft.calcAvg(stFreqs[i], endFreqs[i]);
			bandFreq[i] = midFreqs[i];
		}
		beatDetect(bandRes);
		return new float[][] {bandRes,bandFreq};
	}//getFwdZoneBandsFromAudio	
	
	/**
	 * analyze 1st 8 frequencies of harmonic series for each piano note
	 * @param harmSeries 
	 * @param boundsAra array per key of min frequencies of each key's fundamental and harmonic
	 */
	@Override
	public void getFwdFreqLevelsInHarmonicBands(float[][] keyMinAra, ConcurrentSkipListMap<Float, Integer> res1, ConcurrentSkipListMap<Integer, Float[]> res2, int curIdx){
		float[] keyLoudness = new float[keyMinAra.length-1];		
		//boundsara holds boundaries of min/max freqs for each key
		for (int key=0;key<keyMinAra.length-1; ++key) {keyLoudness[key] = fft.calcAvg(keyMinAra[key][0], keyMinAra[key+1][0]);}			
		for (int i=0;i<keyMinAra.length-1; ++i) {
			float freqLvl=0;
			for (int h=0; h < keyMinAra[i].length;++h) {freqLvl += fft.calcAvg(keyMinAra[i][h], keyMinAra[i+1][h]);}// fft.getFreq(harmSeries[i][h]);
			freqLvl *= keyLoudness[i]/keyMinAra[i].length;		//weighting by main key level
			res1.put(freqLvl, i);
			Float[] tmp = res2.get(i);
			tmp[tmp.length-1] = freqLvl;			
			//Float divVal = (tmp.length-2.0f), oldVal = tmp[curIdx]/divVal;
			Float oldVal = tmp[curIdx];
			tmp[curIdx] = freqLvl;
			tmp[tmp.length-2] = tmp[tmp.length-2] - oldVal + freqLvl; 			
//			tmp[curIdx] = freqLvl;			
//			float tmpSum = tmp[0];for(int t=1;t<tmp.length-2;++t) {tmpSum+=tmp[t];}
//			tmp[tmp.length-2] = tmpSum/(tmp.length-2);			
			//res2.put(i, res2.get(i));
		}		
		float maxLvl = res1.firstKey();		
		barDispMaxLvl[1] = (barDispMaxLvl[1] < maxLvl ? maxLvl : barDispMaxLvl[1]);
	}//getFwdFreqLevelsInHarmonicBands
	
	@Override
	public boolean isPlaying() {return playMe.isPlaying();}
	//song control
	@Override
	public void play() {	playMe.play();}
	@Override
	public void play(int millis) {	if(millis>=songLength) playMe.play(); else playMe.play(millis);}
	@Override
	public void pause() {	playMe.pause(); if (playMe.position() >= .95*songLength) {playMe.rewind();}}
	@Override
	public void rewind() {  playMe.rewind();}
	@Override
	public float[] mixBuffer() {return playMe.mix.toArray();}
	//change current playing location
	@Override
	public void modPlayLoc(float modAmt) {
		int curPos = playMe.position();	
		int dispSize = songLength/20, newPos = (int) (curPos + (dispSize * modAmt));
		if(newPos < 0) { newPos = 0;} else if (newPos > songLength-1){newPos = songLength-1;}
		playMe.cue(newPos);
	}	
	
	@Override
	public boolean donePlaying() {return playMe.position() >= .95*songLength;}
	//get position in current song
	@Override
	public int getPlayPos() {return playMe.position();}
}//myMP3SongHandler


//handle midi songs
class myMidiSongHandler extends mySongHandler{
	// what we need from JavaSound for sequence playback
	private Sequencer sequencer;
	// holds the actual midi data
	private Sequence sequence;
	// output pipe
	public AudioOutput out;
	
	public myMidiSongHandler(Minim _minim, String _fname, String _dname, int _sbufSize) {
		super(_minim,_fname, _dname, _sbufSize);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected void stepAudio() {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void setupFreqArasIndiv() {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void setForwardValsIndiv(WindowFunction win) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean isPlaying() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void play() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void play(int millis) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void pause() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void rewind() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public float[] mixBuffer() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void modPlayLoc(float modAmt) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean donePlaying() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int getPlayPos() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float[][] getFwdBandsFromAudio() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public float[][] getFwdZoneBandsFromAudio() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void getFwdFreqLevelsInHarmonicBands(float[][] keyMinAra, ConcurrentSkipListMap<Float, Integer> res1, ConcurrentSkipListMap<Integer, Float[]> res2, int curIdx) {
		// TODO Auto-generated method stub
		
	}	

	//need JavaSound interface receiver in order to be send midi messages from the Sequencer.
	//we then set an instance of this class as the Receiver
	//for on of the Sequencer's Trasmitters.
	//See: http://docs.oracle.com/javase/6/docs/api/javax/sound/midi/Receiver.html
	class MidiReceiver implements Receiver{
		public void close() {}

		public void send( MidiMessage msg, long timeStamp ){ 
			// we only care about NoteOn midi messages.
			// here's how you check for that
			if ( msg instanceof ShortMessage ){
				ShortMessage sm = (ShortMessage)msg;
				// if you want to handle messages other than NOTE_ON, you can refer to the constants defined in 
				// ShortMessage: http://docs.oracle.com/javase/6/docs/api/javax/sound/midi/ShortMessage.html
				// And figure out what Data1 and Data2 will be, refer to the midi spec: http://www.midi.org/techspecs/midimessages.php
				if ( sm.getCommand() == ShortMessage.NOTE_ON ){
					// note number, between 1 and 127
					int note = sm.getData1();
					// velocity, between 1 and 127
					int vel  = sm.getData2();
					// we could also use sm.getChannel() to do something different depending on the channel of the message
	     
					// see below the draw method for the definition of this sound generating Instrument
					out.playNote( 0, 0.1f, new Synth( note, vel ) ); 
				}
			}
		}
	}
	// the Instrument implementation we use for playing notes we have to explicitly specify the Instrument interface
	// from Minim because there is also an Instrument interface in javax.sound.midi. 
	class Synth implements ddf.minim.ugens.Instrument{
		public Oscil wave;
		public Damp env;
		public int noteNumber;
		
		public Synth( int note, int velocity ){
			noteNumber = note;
			float freq = Frequency.ofMidiNote( noteNumber ).asHz();
			float amp  = (float)(velocity-1) / 126.0f;
		  
			wave = new Oscil( freq, amp, Waves.QUARTERPULSE );
			// Damp arguments are: attack time, damp time, and max amplitude
			env  = new Damp( 0.001f, 0.1f, 1.0f );
		  
			wave.patch( env );
		}//ctor
		
		public void noteOn( float dur ){
			//attach visual here
			// make sound
			env.activate();
			env.patch( out );
		}
		
		public void noteOff(){
			env.unpatchAfterDamp( out );
		}
	}
	
}//myMidiSongHandler