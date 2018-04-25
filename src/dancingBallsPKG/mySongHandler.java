package dancingBallsPKG;

import java.io.*;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentSkipListMap;

import ddf.minim.*;
import ddf.minim.analysis.*;
import ddf.minim.ugens.*;

//to handle midi communication
import javax.sound.midi.*;

//songs managed and analyzed via UI
public abstract class mySongHandler {
	public DancingBalls pa;
	protected Minim minim;
	public AudioFile songFile;
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
	
	public boolean isMidi;
	
	//level multiplier to facilitate display of volume histogram
	protected float lvlMult = 200.0f;
	//sample resolution - # of samples to be averaged for each value in songlvls
	protected int lvlSampleRes = 10;
	
	public mySongHandler(DancingBalls _pa,Minim _minim, AudioFile _songFile, int _sbufSize) {
		pa=_pa;minim=_minim; songFile = _songFile;		
		songBufSize=_sbufSize;
		barDispMaxLvl = new float[2];		
		barDispMaxLvl[0]=.01f; barDispMaxLvl[1]=.01f;
		sampleRate=0;
		Path filePath = songFile.getFilePathForLoad(this);
		if(filePath == null) {
			pa.outStr2Scr("WARNING in mySongHandler : Attempted to load file classified as directory : " + filePath.toString());
		} else {
			boolean songLoaded=loadAudio(filePath);
			if(!songLoaded) {		pa.outStr2Scr("WARNING in mySongHandler : Failed to load Audio File : " + songFile);}
		}
	}//ctor
	
	public void setForwardVals(WindowFunction win, int fftMinBandwidth, int _numZones) {
		numZones = _numZones;//number of zones for dancing ball (6)
		minFreqBand = fftMinBandwidth;//minimum frequency to consider (== minimum bandwidth fft is required to handle)
		setupFreqAras();
		barDispMaxLvl = new float[2]; barDispMaxLvl[0]=.01f; barDispMaxLvl[1]=.01f;
		//initialize beat detection data
		initBeatDet();
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
	
	//draws a window of samples of numSmpls in length, 
	//starting at stTime, avging over consecutive values of binSize
	//binsize acts like a resolution parameter
	public void drawSongLvls(int maxNumSmpls, int binSize, float barHt) {
		if(binSize < 1) {return;}
		long curTicks = getPlayTicks(), stTime = (curTicks - ((maxNumSmpls*binSize)/2));//backpedal st time to be half maxNumSmpls behind current time
		if (stTime < 0) {stTime = 0;}
		float[] songlvls = getLvlAraWindow(stTime, maxNumSmpls, binSize);
		long curPos = (curTicks - stTime)/binSize;
		pa.pushMatrix();pa.pushStyle();
		pa.strokeWeight(1.0f);
		for(int i=0;i<songlvls.length;++i) {
			if(i>curPos) {
			pa.setColorValFill(pa.gui_Red);
			pa.setColorValStroke(pa.gui_Red);
			}
			else {
			pa.setColorValFill(pa.gui_White);
			pa.setColorValStroke(pa.gui_White);
			}
			pa.line(0,0,0,0,-songlvls[i]*barHt,0);
			pa.translate(1.0f, 0, 0);
		}	
		pa.popStyle(); pa.popMatrix();
	}//drawSongLvls
	
	
	//check this to see if beat has been detected
	public boolean[] beatDetectZones() {
		boolean[] retVal = new boolean[fIsOnset.length];
		for(int i=0;i<fIsOnset.length;++i) {retVal[i]=fIsOnset[i];}
		return retVal;
	}//beatDetectZones	
	private float calcMean(float[] ara){float avg=ara[0];for (int i=1; i<ara.length; ++i){avg += ara[i];}avg /= ara.length;return avg;}
	private float specAverage(float[] ara){float avg = 0,num = 0;for (int i=1; i<ara.length; ++i){	if (ara[i]>0)	{avg += ara[i];++num;}	}if (num > 0){avg /= num;}return avg;}
	private float calcVar(float[] ara, float val){float V = (float)Math.pow(ara[0] - val, 2);for(int i=1; i<ara.length; ++i){V += (float)Math.pow(ara[i] - val, 2);} V/=ara.length;return V;}		
	
	//load audio
	protected abstract boolean loadAudio(Path fileName);
	//grab next set of samples/data to analyze for audio
	protected abstract void stepAudio();
	//individual handling of setup function
	protected abstract void setupFreqArasIndiv();
	protected abstract void setForwardValsIndiv(WindowFunction win);
	
	//get array of song volume levels at passed values, to be used for lvl display during playback
	protected abstract float[] getLvlAraWindow(Long stTime, int numSmpls, int binSize);
	
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
	
	//get position in ticks in current song - for audio this is TBD, for midi it is self explanatory
	public abstract long getPlayTicks();
	
	//analysis results
	public abstract float[][] getFwdBandsFromAudio();
	public abstract float[][] getFwdZoneBandsFromAudio();
	public abstract void getFwdFreqLevelsInHarmonicBands(float[][] keyMinAra, ConcurrentSkipListMap<Float, Integer> res1, ConcurrentSkipListMap<Integer, Float[]> res2, int curIdx, int instIDX);
	
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
	public myMP3SongHandler(DancingBalls _pa,Minim _minim,  AudioFile _songFile, int _sbufSize) {
		super(_pa,_minim,_songFile, _sbufSize);
		songLength = playMe.length();
		fft = new FFT(playMe.bufferSize(), playMe.sampleRate() );
		insertAt = 0;
		sensitivity = 10;	
		sampleRate = playMe.sampleRate();
		isMidi = false;
		//System.out.println("Song: " + dispName + " sample rate : " + playMe.sampleRate());
	}

	@Override
	protected boolean loadAudio(Path filePath) {
		playMe = minim.loadFile(filePath.toString(), songBufSize);
		return true;
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
		//fft-related indexes for starting and ending frequencies for each zone
		stFreqIDX = new int[numZones]; endFreqIDX = new int[numZones];
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
	@Override
	//get array of song volume levels at passed values, to be used for lvl display during playback
	protected float[] getLvlAraWindow(Long stTime, int numSmpls, int binSize) {
		//TODO
		return new float[numSmpls];
	}

	
	
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
	public void getFwdFreqLevelsInHarmonicBands(float[][] keyMinAra, ConcurrentSkipListMap<Float, Integer> res1, ConcurrentSkipListMap<Integer, Float[]> res2, int curIdx, int instIDX){
		//instChans is ignored, used for midi songs to specify midi channel
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
	//return the buffer of audio samples currently being played
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
	//for mp3 this is same as getPlayPos, used for lvl display during playback
	@Override
	public long getPlayTicks() {return playMe.position();}

}//myMP3SongHandler


//analyse midi songs 
class myMidiSongHandler extends mySongHandler{
	// what we need from JavaSound for sequence playback
	private Sequencer sequencer;
	// holds the actual midi data
	//private Sequence sequence;
	// output pipe
	public AudioOutput out;
	private Long songTickLen, startTickLoc;
	private float ticksPerMillis;
	//most recent start of this sequence, used to subtract current time to find...
	private int startTime;
	//midi receiver manages 
	private MidiReceiver midiRec;
	
	//up to 16 channels of up to 127 notes playing - need to block on this ?
	public float[][] midi_notesLvls, pianoNoteLvls;
	
	public myMidiFileAnalyzer mfa;
	
	
	public myMidiSongHandler(DancingBalls _pa,Minim _minim, AudioFile _songFile, int _sbufSize) {
		super(_pa,_minim,_songFile, _sbufSize);
		sampleRate = out.sampleRate();
		ticksPerMillis = 1;
		isMidi = true;
		
	}//myMidiSongHandler
		
	@Override
	//get array of song volume levels at passed values, to be used for lvl display during playback
	protected float[] getLvlAraWindow(Long stTime, int numSmpls, int binSize) {		return mfa.getRelSongLevelsWin(stTime, numSmpls, binSize);	}
	
	@Override
	protected boolean loadAudio(Path filePath) {
		mfa = new myMidiFileAnalyzer(songFile, -1);
		if (mfa.loadAudio()) {
			mfa.analyze();
			try {
				sequencer= MidiSystem.getSequencer(false);
			    sequencer.open();
			    sequencer.setSequence(mfa.sequence);
				out = minim.getLineOut();	
			    midiRec = new MidiReceiver(out, this);
			    // hook up an instance of our Receiver to the Sequencer's Transmitter
			    sequencer.getTransmitter().setReceiver(midiRec);		    
			    midi_notesLvls = new float[16][];
			    pianoNoteLvls = new float[16][];
			    for(int i=0;i<midi_notesLvls.length;++i) {	midi_notesLvls[i] = new float[127]; pianoNoteLvls[i] = new float[88];   }//piano keys start at idx 21 lvls per key
				songLength = (int) (mfa.sequence.getMicrosecondLength()/1000);
				songTickLen = mfa.midiSong.tickLen;
				ticksPerMillis = songTickLen/(1.0f*songLength);			
			}
			catch( MidiUnavailableException ex ){ System.out.println( "No default sequencer." );return false;}
			catch( InvalidMidiDataException ex ){System.out.println( "The mid file was not a midi file." );return false;}
			return true;
		} else {
			return false;
		}
	}//loadAudio	
	
	
	@Override
	protected void stepAudio() {
		//get currently playing notes on all channels in midi receiver - might have to block on midi_notesLvls since possibly running in separate thread(?)
		for(int i=0;i<midi_notesLvls.length; ++i) {
			System.arraycopy(midi_notesLvls[i], 20, pianoNoteLvls[i], 0, pianoNoteLvls[i].length);
		}
	}

	@Override
	public boolean isPlaying() {return sequencer.isRunning();}
	@Override
	public void play() {sequencer.start();}
	@Override
	public void play(int millis) {//set play start position, in millis from beginning
		sequencer.setLoopStartPoint((long) (ticksPerMillis * millis));
		sequencer.start();
	}

	@Override
	public void pause() {
		sequencer.stop();
	}

	@Override
	public void rewind() {sequencer.setLoopStartPoint(0);	}
	@Override //uses audio output from minim output stream
	public float[] mixBuffer() {
		return out.mix.toArray();
	}

	@Override
	public void modPlayLoc(float modAmt) {
		long curPos = sequencer.getTickPosition();
		int dispSize = songLength/20;
		long newPos = (long) (curPos + (dispSize * modAmt));
		if(newPos < 0) { newPos = 0;} else if (newPos > songLength-1){newPos = songLength-1;}
		sequencer.setLoopStartPoint(newPos);
	}

	@Override
	public boolean donePlaying() {
		long curPos = sequencer.getTickPosition();
		return ((curPos >= songTickLen-1) || (!sequencer.isRunning()));
	}
	
	@Override 
	public long getPlayTicks() {return sequencer.getTickPosition();}

	@Override
	public int getPlayPos() {	return (int) (sequencer.getTickPosition()/ticksPerMillis);	}

	@Override
	public float[][] getFwdBandsFromAudio() {//all audio bands from fft
		// TODO Auto-generated method stub
		return new float[2][0];
	}

	@Override
	public float[][] getFwdZoneBandsFromAudio() {//all zone bands from fft
		// TODO Auto-generated method stub
		return new float[2][0];
	}

	@Override //provides levels from midi commands
	public void getFwdFreqLevelsInHarmonicBands(float[][] keyMinAra, ConcurrentSkipListMap<Float, Integer> res1, ConcurrentSkipListMap<Integer, Float[]> res2, int curIdx, int instIDX) {
		float[] keyLoudness = new float[keyMinAra.length-1];		
		//either consider all channels together, or specify a channel instChans
		//for (int chnIDX = 0;chnIDX<instChans.length;++chnIDX) {
			for (int key=0;key<keyLoudness.length; ++key) {keyLoudness[key] += pianoNoteLvls[instIDX][key];}			
		//}
		for (int key=0;key<keyLoudness.length; ++key) {
			float freqLvl = keyLoudness[key];
			res1.put(freqLvl, key);
			Float[] tmp = res2.get(key);
			tmp[tmp.length-1] = freqLvl;			
			Float oldVal = tmp[curIdx];
			tmp[curIdx] = freqLvl;
			tmp[tmp.length-2] = tmp[tmp.length-2] - oldVal + freqLvl; 				
		}	
	
		float maxLvl = res1.firstKey();		
		barDispMaxLvl[1] = (barDispMaxLvl[1] < maxLvl ? maxLvl : barDispMaxLvl[1]);
	}	
	@Override
	protected void setupFreqArasIndiv() {}
	@Override
	protected void setForwardValsIndiv(WindowFunction win) {}

}//myMidiSongHandler

//need JavaSound interface receiver in order to send midi messages from the Sequencer.
//we then set an instance of this class as the Receiver
//for one of the Sequencer's Trasmitters.
//See: http://docs.oracle.com/javase/8/docs/api/javax/sound/midi/Receiver.html
class MidiReceiver implements Receiver{
	private AudioOutput out;
	
//	//up to 16 channels of up to 127 notes playing -- note, only 16 channels of audio.  midi tracks != channels
	public Synth[][] instrNotes;
	//owning handler - send notes to mapping array
	public myMidiSongHandler hndl;
	
	public MidiReceiver(AudioOutput _out, myMidiSongHandler _hndl) {
		out=_out;hndl=_hndl;
		instrNotes = new Synth[16][];
		Synth[] tmpAra;
		for(int ch=0;ch<instrNotes.length;++ch) {
			tmpAra = new Synth[127];
			//TODO make different instruments for each midi channel
			for(int i=0;i<tmpAra.length;++i) {tmpAra[i] = new Synth(out, (i+1), 10);}
			instrNotes[ch]=tmpAra;
		}
	}
	public void close() {}

	public void send(MidiMessage msg, long timeStamp){ 
		if (msg instanceof ShortMessage){
			ShortMessage sm = (ShortMessage)msg;
			int command = sm.getCommand();
			int chan = sm.getChannel();
			switch(command) {
				case ShortMessage.NOTE_ON : {					
					int note = sm.getData1();		// note number, between 1 and 127
					int vel  = sm.getData2();		// velocity, between 1 and 127 (if 0 means note off)
					instrNotes[chan][note-1].setAmplitude(vel);
					hndl.midi_notesLvls[chan][note-1] = instrNotes[chan][note-1].getTtlAmplitude();
					out.playNote(0, 100.0f, instrNotes[chan][note-1]); 
					break;}
				case ShortMessage.NOTE_OFF :{
					// note number, between 1 and 127
					int note = sm.getData1();
					int vel  = sm.getData2();		
					// velocity, between 1 and 127
					instrNotes[chan][note-1].setAmplitude(vel);
					hndl.midi_notesLvls[chan][note-1] = 0;
					instrNotes[chan][note-1].noteOff();
					break;}
			}//switch
			
		// refer to the constants defined in 
		// ShortMessage: http://docs.oracle.com/javase/8/docs/api/javax/sound/midi/ShortMessage.html
		// what Data1 and Data2 will be, refer to the midi spec: http://www.midi.org/techspecs/midimessages.php
		}
	}
}//MidiReceiver

// the Instrument implementation we use for playing notes: we have to explicitly specify the Instrument interface
// from Minim because there is also an Instrument interface in javax.sound.midi. 
class Synth implements ddf.minim.ugens.Instrument{
	public Oscil[] waves;
	public Damp env;
	public int noteNumber;
	private AudioOutput out;
	public float[] ampAra;					//array of amplitudes for each wave gen
	public float ttlAmp, divMult = 2.0f;					//divisor muliplier for each wave in harmonic series
	public Summer sum;
	public Synth(AudioOutput _out, int note, int velocity ){
		out=_out;noteNumber = note;
		float freq = Frequency.ofMidiNote(noteNumber).asHz();
		waves = new Oscil[8];
		ampAra = new float[waves.length];
		setBaseAmplitude(velocity);
		sum = new Summer();		
		// Damp arguments are: attack time, damp time, and max amplitude
		env = new Damp( 0.001f, 1.0f, 1.0f );
		for(int i=0;i<waves.length;++i) {
			waves[i]=new Oscil(freq*(i+1), ampAra[i], Waves.SINE);
			waves[i].patch(sum);
		}
		//waves[0].patch(sum);
//		//tie waves generator into envelope
		sum.patch(env);
	}//ctor
	
	//set array of per-waveform amplitudes
	protected void setBaseAmplitude(int velocity) {
		float div = 1.0f, baseAmpl = (float)(velocity-1) / 126.0f;
		ttlAmp = 0;
		for(int i=0;i<ampAra.length; ++i) {
			ampAra[i] = baseAmpl/div;
			div *= divMult;	
			ttlAmp += ampAra[i];
		}	
	}//setBaseAmplitude
	
	public void setAmplitude(int velocity) {
		setBaseAmplitude(velocity);
		for(int i=0;i<waves.length;++i) {waves[i].setAmplitude(ampAra[i]);}
	}
	
	public float getTtlAmplitude() {return ttlAmp;	}
	
	public void noteOn(float dur ){
		//make sound
		env.setDampTime(1.0f);
		env.activate();
		env.patch(out);
	}
	
	public void noteOff(){
		//stop sound - remove envelope from output
		env.setDampTime(0.1f);
		env.unpatchAfterDamp(out);
	}
}
