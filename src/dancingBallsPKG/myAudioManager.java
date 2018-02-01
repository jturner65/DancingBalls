package dancingBallsPKG;

import java.util.*;
import java.util.concurrent.*;

import ddf.minim.analysis.*;
import processing.core.PConstants;

//class to hold all audio and audio analysis - moved from window class
public class myAudioManager {
	public DancingBalls pa;
	public DancingBallWin win;
	
	public static final int numZones = DancingBallWin.numZones;
	//piano visualization object
	public myPianoObj dispPiano;	
	
	public int[] flags;
	public static final int 
			debugIDX			= 0,
			songHndlrLoadedIDX	= 1,
			audioLoadedIDX		= 2,
			audFMgrLoadedIDX	= 3;
	public static final int numFlags = 4;	
	//handled sample rates based on songs loaded - put sample rates in keys
	public ConcurrentSkipListMap<Float, Integer> sampleRates;	
	//idx's of arrays for different analysis results
	public static final int
		dftResIDX = 0,
		fftResIDX = 1;
	
	//idx 0 is result from analyzing 1st 8 frequencies of harmonic series for each piano note
	//idx 1 holds results from fft analysis - magnitude key, value is index of note with max level within min/max note bounds;
	public ConcurrentSkipListMap<Float, Integer>[] lvlsPerPKey;
	//result from analyzing frequencies, keyed by key, value == levels for dft (0) and fft(1)
	//array of values is the past n values, plus one level for sum over past n values, and last index is current level
	//neighborhood (past values) size is keyLvlLen - take average over last keyLvlLen values, size of array is keyLvlLen+2
	public ConcurrentSkipListMap<Integer, Float[]>[] perKeyLvls;
	//each array in perKeyLvls is this + 2 in length (last 2 values are sum of first keyLvlLen values and current value, respectively
	public final int keyLvlLen=5;
	//current index in perKeyLvls array we are accessing - always mod keyLvlLen
	public int curKeyLvlIdx = 0;
	//array of threshold values based on max value seen this cycle : TODO change to handle indiv zones 
	public float[] minAudThres;
	
	//results from indiv analysis of all threads; perThLvlBandsPerKey is smaller array with aggregate results for subsets of threads
	public ConcurrentSkipListMap<Float, Integer>[] perThLvlPerKey, perThLvlBandsPerKey;	
	//one entry per analysis band - TODO reconfigure for per-song analysis - group each thread(which covers a frequency zone) depending on nature of song
	private int[][] perThLvlIDXs = new int[][] {{0,1,2,3},{4,5,6,7},{8,9}};
	
	//set by window UI selection
	// 0:global max, 1:freq zone max or 2:per-thread max note levels
	public static int dftThdResToShow = 0;
	//noise gate lvl - absolute level of audio below which audio is ignored
	public static float noiseGateLvl = 0.1f;	
	//structure holding multiple melody candidates - millis from beginning as key, value is map of melody candidate keys at that time stamp, keyed by lvl
	//idx 0 is dft melody candidates, idx 1 is fft melody candidates
	public ConcurrentSkipListMap<Integer, ConcurrentSkipListMap<Float, Integer>>[] melodyCandidates;	
	//time when song starts playing in millis
	public int songStartMillis, pauseTimeMillis, curTimeFromStartMillis;
	//sample count from start of song
	public int curTimeFromStartSmpl;
	
	//minim audio-related variables
	public final int fftMinBandwidth = 20, fftBandsPerOctave = 24;
	//per zone avg frequencies
	public float[] bandRes = new float[numZones], bandFreqs = new float[numZones], 
			allBandsRes = new float[numZones], allBandFreqs = new float[numZones];
	public boolean[] beatDetRes = new boolean[numZones], lastBeatDetRes = new boolean[numZones];
	//threads for working on dft analysis
	public List<myDFTNoteMapper> callDFTNoteMapper;
	public List<Future<Boolean>> callDFTMapperFtrs;
	
	//structure for monitoring key-ons - used to determine when keys start, and to smooth neighborhoods
	private int[][] numFramesOn = new int[2][myPianoObj.numKeys];
	private boolean[][] turnOnKey = new boolean[2][myPianoObj.numKeys],turnOffKey = new boolean[2][myPianoObj.numKeys];	
	
	//# of consecutive samples before we stop incrementing
	private int frameWindow = 10;
	//per bank arrays of buffer size, song handlers, song file names
	//current song index and songBank (bank corresponds to songs or piano samples)
	//list of song banks - use to pick either songs or piano notes or midi files
	public static int songType = 1, songBank = 2, songIDX = 1;
	
	//handler to manage structure of audio files under "data" dir
	public myAudioFileManager audioFileIO;
	
	//threshold below which audio is ignored - fraction of max level seen. set by UI
	public static float audThreshold = .55f;
	
	//TODO : replace with audio manager
	public static String[] songDirList = new String[] {"Midi", "Audio"};	
	public static String[][] songBanks = new String[][] {{"Midi"},{"Piano Scales","Piano Songs", "Misc Songs", "Bach Cello", "Piano Notes"}};
	//list of song names
	public static String[][][] songList = new String[][][]{
		{{"Fugue1 Midi","Fugue 2 Midi","Fugue 3 Midi"}},
		{{"Chromatic F#","Ab","A","Bb","B","C#","C","D","Eb","E","F#","F","G","Pentatonic F#","WholeTone C#","WholeTone C"},		
		{"WellTmprdClav CMaj","Sati-Gnoss1","Sati-Gymn1","Fur Elise"},
		{"PurpleHaze","UNATCO","Hunting","SavanaDance","Karelia","Choir"},
		{"Cello4 EbMaj","Cello5 Cmin"},
		{"ff-029","ff-030","ff-031","ff-050","ff-051","ff-052","ff-053","ff-054"}}};
	//song buffer size on per-bank basis
	//public final int songBufSize = 1024;
	public final int[] songBufSize = new int[] {1024,1024,1024, 2048, 2048, 1024};
	//which dft function to use - needs an entry per song
	//0:per sample, all harms, 1 : per sample, fund only, 2:all samples, fund only
	//set by UI
	public static int calcFuncToUse = 1;
	public mySongHandler[][][] songs;
	//0==mp3, 1==midi, used to determine display strings for some UI features
	public int [][][] songTypes;
	public String[][][] songFilenames = new String[][][]{
		{{"fugue1.mid","fugue2.mid","fugue3.mid"}},
		{{"scales_ChromaticF Sharp.mp3","scales_A Flat.mp3","scales_A.mp3",
		"scales_B Flat.mp3","scales_B.mp3","scales_C Sharp.mp3","scales_C.mp3","scales_D.mp3","scales_E Flat.mp3","scales_E.mp3",
		"scales_F Sharp.mp3","scales_F.mp3","scales_G.mp3","scales_Pentatonic on F Sharp.mp3","scales_WholeToneC Sharp.mp3","scales_WholeToneC.mp3"},
		{"WTK_Cmaj.mp3","satie_gnossienne1.mp3","satie_gymnopedie1.mp3","FurElise.mp3"},
		{"PurpleHaze.mp3","UNATCO.mp3","Hunting.mp3","SavanaDance.mp3","karelia.mp3","choir.mp3"},
		{"Bach cello No. 4 in EbMaj_Prelude.mp3","Bach cello No. 5 in CMin_Prelude.mp3"},
		{"piano-ff-029.wav","piano-ff-030.wav","piano-ff-031.wav","piano-ff-050.wav","piano-ff-051.wav","piano-ff-052.wav","piano-ff-053.wav","piano-ff-054.wav"}}
	};
	
	//New structs to hold current songs, current banks, and current audio file types
	public String[] curSongList, curBankList,curTypeList;
	
	
	//current index of fft windowing function, from ui
	public int curWindowIDX = 0;	
	public WindowFunction[] windowList = new WindowFunction[]{FFT.NONE, FFT.BARTLETT, FFT.BARTLETTHANN, FFT.BLACKMAN, FFT.COSINE, FFT.GAUSS, FFT.HAMMING, FFT.HANN, FFT.LANCZOS, FFT.TRIANGULAR};	
	//beat interaction
	public myBeat[] tapBeats, audioBeats;
		
	//list of intervals found in current song
	public ConcurrentSkipListMap<Integer, myNoteIntervalTuple>[] intervals;
	private int[] intProc = new int[] {0,0};//either 0:add first note, 1: add 2nd note, 2: end 2nd note
	private int[] lastLoudestNote = new int[] {-1,-1};	
	//number of top signal level notes to show per display result (either globally or within thread results). set by UI
	public static int numNotesToShow = 1;	
	
	public myAudioManager(DancingBalls _pa,DancingBallWin _win) {
		pa=_pa; win=_win;dispPiano = win.dispPiano;
		initMe();		
	}//myAudioManager
	
	private void initMe() {
		initFlags(); 
		dispPiano.initPianoFreqs();
		//initialize tap beat structures
		initTapBeatStructs();
//		//load audio IO manager to maintain internal hierarchy of song structures
//		pa.outStr2Scr("Start building audio File IO @ Millis since start of program : " + (pa.timeSinceStart()));
//		//structure holding tree of info regarding audio data on disk
//		//instead being loaded in individual runnable
//		pa.th_exec.execute(new myAudFileMgrLoader(this));			
		//load all songs, add sample rate  		
		songs = new mySongHandler[songDirList.length][][];
		songTypes = new int[songDirList.length][][];
		sampleRates = new ConcurrentSkipListMap<Float, Integer>();//hold only sample rates that we have seen		
		//TODO move this to be called when audio file manager is loaded when built using audio file manager
		pa.th_exec.execute(new mySongLoadMapper(this));
		//launch thread to precalculate all trig stuff : not needed with multi-threading dft calc - math is fast enough without this
		//pa.th_exec.execute(new myTrigPrecalc(this, allFreqsUsed) );
		//build DFT threads and precalc local cos/sin values
		//pa.outStr2Scr("Num threads in myAudioManager : " +pa.numThreadsAvail );
		initDFTAnalysisThrds(pa.numThreadsAvail-2);	
		//changeCurrentSong(songType, songBank, songIDX);
	}//initMe
	
	public void buildAudioFileIO() {
		if((getFlags(audFMgrLoadedIDX)) && (null != audioFileIO)) {
			pa.outStr2Scr("Audio File IO built already");
			win.clearFuncBtnSt_BuildAudioFileIO();
		return;}
		//load audio IO manager to maintain internal hierarchy of song structures
		pa.outStr2Scr("Start building audio File IO @ Millis since start of program : " + (pa.timeSinceStart()));
		//structure holding tree of info regarding audio data on disk
		//instead being loaded in individual runnable
		pa.th_exec.execute(new myAudFileMgrLoader(this));			
	}//buildAudioFileIO
	
	private void initFlags() {flags = new int[1 + numFlags/32]; for(int i = 0; i<numFlags; ++i){setFlags(i,false);}}
	public boolean getFlags(int idx){int bitLoc = 1<<(idx%32);return (flags[idx/32] & bitLoc) == bitLoc;}		
	public void setFlags(int idx, boolean val) {
		boolean curVal = getFlags(idx);
		if(val == curVal) {return;}
		int flIDX = idx/32, mask = 1<<(idx%32);
		flags[flIDX] = (val ?  flags[flIDX] | mask : flags[flIDX] & ~mask);
		switch(idx){
			case debugIDX		: {break;}
			case songHndlrLoadedIDX	: {break;}
			case audioLoadedIDX	: {
				//once audio is loaded, call changeCurrentSong to set up all relevant audio values
				changeCurrentSong(songType, songBank, songIDX);				
				break;}		
			case audFMgrLoadedIDX : {
				if(val) {
					curTypeList = audioFileIO.getTypeSubdirNames();
					pa.outStr2Scr("End building audio File IO @ Millis since start of program : "+ (pa.timeSinceStart()));
					win.clearFuncBtnSt_BuildAudioFileIO();
				}
				break;}
		}
		
	}//setFlags

	//launch preprocessing of midi data
	public void preprocMidiData() {
		if (!getFlags(audFMgrLoadedIDX)) {	pa.outStr2Scr("Audio File IO Manager must be loaded before Midi Data can be processed");return;}//if audiofilemanager is not loaded, then just ignore this request
		//fire and forget midi processing		
		pa.th_exec.execute(new myMidiFileProcMapper(this));
	}//preprocMidiData

	//initialize array of mybeat to hold results of tapped beat data
	protected void initTapBeatStructs() {
		//beat holding array
		tapBeats = new myBeat[numZones];
		audioBeats = new myBeat[numZones];
		int stTime = pa.millis();
		for(int i=0;i<numZones; ++i) {	tapBeats[i] = new myBeat(pa,i,stTime);	audioBeats[i] = new myBeat(pa,i,stTime);	}		
	}//initTapBeatStructs
	
	private mySongHandler getCurrentClip() {return songs[songType][songBank][songIDX];}
	//either returns current song or current piano clip
	//protected mySongHandler getCurrentClip(int bankIdx, int songIdx) {return songs[songType][bankIdx][songIdx];}
	protected mySongHandler getCurrentClip(int newTypeIDX, int bankIdx, int songIdx) {return songs[newTypeIDX][bankIdx][songIdx];}
	
	protected void setFFTVals() {
		for(int t=0;t<songs.length;++t) {
			for(int b=0;b<songs[t].length;++b) {
				for(int i=0;i<songs[t][b].length;++i){songs[t][b][i].setForwardVals(windowList[curWindowIDX], fftMinBandwidth, numZones);}
			}
		}
	}//setFFTVals
	
	public boolean changeCurrentSong(int newSongType, int newSongBank, int newSongIDX){
		//audio not loaded, so reset values to previous values
		if(!getFlags(audioLoadedIDX)){return false;}
		getCurrentClip(songType, songBank, songIDX).pause();//pause current song
		//ball.resetVertLocs();
//		songType = newSongType % curTypeList.length;
//need to modify newSongBank and newSongIDX to be modulo 
//		curBankList = audioFileIO.getBanksInType(songType);
//		curSongList = audioFileIO.getSongsInBank(songType, songBank);
		
		
		//TODO fix this to be directory driven using audioFileIO
		
		songType = (newSongType % songs.length);
		songBank = (newSongBank % songs[songType].length);
		songIDX = (newSongIDX % songs[songType][songBank].length);
		

		//update display buttons based on new song
		win.updateButtons(songTypes[songType][songBank][songIDX]);
		//re-init struct holding keys turned on
		numFramesOn = new int[2][myPianoObj.numKeys];
		turnOnKey = new boolean[2][myPianoObj.numKeys];
		turnOffKey = new boolean[2][myPianoObj.numKeys];
		for(int i=0;i<numFramesOn.length;++i) {
			numFramesOn[i]=new int[myPianoObj.numKeys];
			turnOnKey[i]=new boolean[myPianoObj.numKeys];
			turnOffKey[i]=new boolean[myPianoObj.numKeys];
		}
		intProc = new int[] {0,0};//either 0:add first note, 1: add 2nd note, 2: end 2nd note
		lastLoudestNote = new int[] {-1,-1};
		timeOfLastInterval = new Integer[] {-1,-1};

		//change per-thread values since song has changed
		setDFTRunValsForCurSong();
		
		if(win.getPrivFlags(DancingBallWin.playMP3Vis)){
			boolean res = startAudio();
			//if not playing, turn 'play' button off
			win.setPrivFlags(DancingBallWin.playMP3Vis, res);
		}
		return true;
	}//changeCurrentSong
	
	public void changeCurrentWindowfunc(int newWinFuncIDX) {
		curWindowIDX = newWinFuncIDX;
		if(getFlags(songHndlrLoadedIDX)) {setFFTVals();}
	}//changeCurrentWindowfunc

	public boolean startAudio(){
		//if(!getFlags(audioLoadedIDX)){loadSongsAndFFT();}//load songs if not loaded already
		if(!getFlags(audioLoadedIDX)){return false;}//don't try to play if audio isn't currently loaded
		//pa.outStr2Scr("Song in buffer : " + songTitles[songIDX] + " size: " +  songs[songIDX].bufferSize() + " Sample rate : "+ songs[songIDX].sampleRate());
		//set time of start of song as now
		songStartMillis = pa.millis();
		curTimeFromStartSmpl = 0;
		melodyCandidates[0].clear();
		melodyCandidates[1].clear();
		intervals[0].clear();
		intervals[1].clear();
		//need to reinitialize analysis results structures
		initLvlMaps();
		getCurrentClip().play();
		return true;
	}//startAudio	
	
	//(re) initialize song analysis maps
	public void initLvlMaps(){
		for(int i=0;i<perThLvlPerKey.length;++i) {	perThLvlPerKey[i].clear();	}
		for(int i=0;i<perThLvlBandsPerKey.length;++i) {perThLvlBandsPerKey[i].clear();}
		for(int i=0;i<lvlsPerPKey.length;++i) {lvlsPerPKey[i].clear();}
	}//initLvlMaps

	public void pauseAudio(){		if(getFlags(audioLoadedIDX)){			stopAllPlaying();	}}
	//stop all clips from playing
	protected void stopAllPlaying() {
		pauseTimeMillis = pa.millis();
		for(int t=0;t<songs.length;++t) {for(int b=0;b<songs[t].length;++b){for(int i=0;i<songs[t][b].length;++i){	songs[t][b][i].pause();	}	}	}
	}
	//rewind current song
	public void rewindSong() {if(win.getPrivFlags(DancingBallWin.playMP3Vis)){getCurrentClip().rewind();}}
	
	//get song type given passed int
	public String getSongType(int _typ) {
		String chkStr = null;
		if(_typ==win.midiSong) {		chkStr="midi";} 
		else if(_typ==win.mp3Song) {	chkStr="mp3";} 
		return chkStr;
	}
	
	//convenience function to build descending key-sorted maps
	public ConcurrentSkipListMap<Float, Integer> buildDescMap(){return new ConcurrentSkipListMap<Float, Integer>(new Comparator<Float>() { @Override public int compare(Float o1, Float o2) {   return o2.compareTo(o1);}});}
	
	//init values used by dft
	public void initDFTAnalysisThrds(int numThreads) {
		//threads first numThreads/3 are bass range (0-3 when numThreads == 10)
		//4-6 are mid range
		//7-9 are treble range.  perhaps use these to calculate zone behavior?
		callDFTNoteMapper = new ArrayList<myDFTNoteMapper>();
		callDFTMapperFtrs = new ArrayList<Future<Boolean>>();
		int numPerThread = 1+(dispPiano.pianoFreqsHarmonics.length-1)/numThreads;
		int stIdx = 0, endIdx = numPerThread-1;
		for (int i =0;i<numThreads;++i) {		
			callDFTNoteMapper.add(new myDFTNoteMapper(this,stIdx,endIdx));	
			stIdx = endIdx + 1;
			endIdx = stIdx + numPerThread-1;
			if(endIdx > dispPiano.pianoFreqsHarmonics.length - 1) {endIdx = dispPiano.pianoFreqsHarmonics.length-1;}
		}
		pa.outStr2Scr("DFT Threads configured.");
		lvlsPerPKey = new ConcurrentSkipListMap[2];
		perKeyLvls = new ConcurrentSkipListMap[2];
		minAudThres = new float[2];
		for(int i=0;i<2;++i) {
			lvlsPerPKey[i] = buildDescMap();
			perKeyLvls[i] = new ConcurrentSkipListMap<Integer, Float[]>();
		}
		numFramesOn = new int[2][myPianoObj.numKeys];
		turnOnKey = new boolean[2][myPianoObj.numKeys];
		turnOffKey = new boolean[2][myPianoObj.numKeys];
		for(int i=0;i<numFramesOn.length;++i) {
			numFramesOn[i]=new int[myPianoObj.numKeys];
			turnOnKey[i]=new boolean[myPianoObj.numKeys];
			turnOffKey[i]=new boolean[myPianoObj.numKeys];
		}
		
		for(int t=0;t<dispPiano.numKeys;++t) {
			Float[] tmp1 = new Float[keyLvlLen+2];
			Float[] tmp2 = new Float[keyLvlLen+2];
			for(int f=0;f<tmp1.length;++f) {tmp1[f]=0.0f;tmp2[f]=0.0f;}
			perKeyLvls[dftResIDX].put(t, tmp1);
			perKeyLvls[fftResIDX].put(t, tmp2);			
		}
		//intervals from melody candidates
		intervals = new ConcurrentSkipListMap[2];
		intervals[dftResIDX] = new ConcurrentSkipListMap<Integer, myNoteIntervalTuple> ();
		intervals[fftResIDX] = new ConcurrentSkipListMap<Integer, myNoteIntervalTuple> ();
		//melody candidates - a map of key/level tuples for each candidate, indexed by song time 
		//basically the top n candidates at every buffer sample
		melodyCandidates = new ConcurrentSkipListMap[2];
		melodyCandidates[dftResIDX] = new ConcurrentSkipListMap<Integer, ConcurrentSkipListMap<Float, Integer>>(new Comparator<Integer>() { @Override public int compare(Integer o1, Integer o2) {   return o2.compareTo(o1);}});
		melodyCandidates[fftResIDX] = new ConcurrentSkipListMap<Integer, ConcurrentSkipListMap<Float, Integer>>(new Comparator<Integer>() { @Override public int compare(Integer o1, Integer o2) {   return o2.compareTo(o1);}});
		
		//per thread and per combined thread calculations
		perThLvlPerKey = new ConcurrentSkipListMap[numThreads];
		perThLvlBandsPerKey = new ConcurrentSkipListMap[perThLvlIDXs.length];
		for(int i=0;i<perThLvlPerKey.length;++i) {	perThLvlPerKey[i] = buildDescMap();	}
		for(int i=0;i<perThLvlBandsPerKey.length;++i) {	perThLvlBandsPerKey[i] = buildDescMap();	}
	}

	//set song-dependent values in each thread when song changes
	private void setDFTRunValsForCurSong() {
		mySongHandler song = getCurrentClip();
		//pa.outStr2Scr("setDFTRunValsForCurSong : Setting values in threads for song bank : " + songBank);
		for(int i=0;i<perThLvlPerKey.length;++i) {
			callDFTNoteMapper.get(i).setPerSongValues(song.sampleRate, songBufSize[songBank], (i < 4 ? 2 : 1));
		}
	}//setDFTRunValsForCurSong
	
	
	//set every time this is run before execution
	private void setPerRunRes(float[] _buffer) {
		initLvlMaps();
		//curKeyLvlIdx is index in per-key lvls array that we are currently populating - to keep running average of values
		curKeyLvlIdx = ((curKeyLvlIdx + 1) % keyLvlLen);
		boolean usePianoTemp = win.getPrivFlags(DancingBallWin.usePianoTune);// usePianoTune[songBank][songIDX];
		curTimeFromStartMillis = pa.millis() - songStartMillis;
		//boolean debug = songBank >= 2;
		for(int i=0;i<perThLvlPerKey.length;++i) {callDFTNoteMapper.get(i).setPerRunValues(_buffer, curKeyLvlIdx, usePianoTemp, lvlsPerPKey[dftResIDX],perKeyLvls[dftResIDX], perThLvlPerKey[i], calcFuncToUse);}
		//set values for "zone" collections of thread bands
		for(int i=0;i<perThLvlBandsPerKey.length;++i) {
			for(int j=0;j<perThLvlIDXs[i].length;++j) {   callDFTNoteMapper.get(perThLvlIDXs[i][j]).setPerRunSharedMap(perThLvlBandsPerKey[i]);}
		}
	}//setPerRunRes
	
	//check if neigbhor to particular piano key is already in map, or check if neighbor is in map but value is less (for last time)
	private boolean getIfNeighborInMap(ConcurrentSkipListMap<Integer, Float> lastMap, boolean chkNumTimesOn, Integer key, Float keyLvl, int curFTIdx) {//, boolean useSumLvl) {
		int chkBnds = 1;
		if(lastMap == null) {return false;}
		if(!chkNumTimesOn) {//just check membership - see if neighbors present
			for(int i=-chkBnds;i<=chkBnds;++i) {
				if(i==0) {continue;}
				if(lastMap.keySet().contains(key+i)) {return true;}
			}
		} else {
			//check if key's neighbors in map, and if so, check if # times on is greater than neighbor - if so then change neighbor
			//if this has been on frame window length of time, then return false - add this note to candidates.  will still decay
			if(numFramesOn[curFTIdx][key] >= frameWindow) {return false;}
			//check neighbors - neighbor may have been on last time and now has higher or lower level than this key, or was not on last time and has higher or lower level than now 
			for(int i=-chkBnds;i<=chkBnds;++i){
				if(0==i)  {continue;}//don't compare to self
				int nKey = key + i;
				if((nKey < 0) || (nKey >= numFramesOn[curFTIdx].length)) {continue;}
				if(numFramesOn[curFTIdx][nKey] > numFramesOn[curFTIdx][key]) {//neighbor has been on longer than this key, so regardless of level
					
					return true;
				} else if(lastMap.keySet().contains(nKey)) {
					
					return true;}//neighbor already has been seen and added
			}
		}		
		return false;
	}//getIfNeighborInMap
	
	
	//situation can happen where note is briefly louder than neighbor, but neighbor is correct note.
	//for this to happen :
	//1 neigbhor is relatively loud
	//2 neighbor might have been playing already (tuning drift as note is sustained)
	//3 neighbor is main note, but this is initial hit of key (note attack is driving note sharp).
	//4 neighbor is within halfstep of this note.	
	//5 incorrect note will generally not last long - few samples (~5 or 6)
	
	//private ConcurrentSkipListMap<Integer, Float>lastTmpTestMap = new ConcurrentSkipListMap<Integer, Float>();
	
	//process the level results of key values after each multi-thread run
	//calcIDX is index to use in maps of lvls and keys - 0==dft, 1==fft
	//set current time and put top x candidates for melody in array melodyCandidates
	private void procPerRunRes(int calcIDX, int animMillis) {
		//set global min audio threshold for display based on loudest key - set by UI
		if(lvlsPerPKey[calcIDX].size() == 0) {return;}
		minAudThres[calcIDX] = audThreshold * lvlsPerPKey[calcIDX].firstKey();
		ConcurrentSkipListMap<Float, Integer> tmp = buildDescMap();
		//if noisy audio and loudest key is less than noisegate lvl, this will not process melody candidates, puts blank melody map in current position of melody candidates structure
		if(lvlsPerPKey[calcIDX].firstKey() >= this.noiseGateLvl) {//only process if loudest is louder than threshold
			//temp map to hold all keys that have been added, as keys, to test for locality
			ConcurrentSkipListMap<Integer, Float> tmpTestMap = new ConcurrentSkipListMap<Integer, Float>();
			boolean useSumLvl = win.getPrivFlags(DancingBallWin.useSumLvl);
			Float lvl;
			Float[] lvlAra;
			for(Float lvlIdx : lvlsPerPKey[calcIDX].keySet()) {//descending order of level
				if(lvlIdx <= minAudThres[calcIDX]) {break;}
				Integer key = lvlsPerPKey[calcIDX].get(lvlIdx);//key producing this particular lvl of response
				if(useSumLvl) {
					lvlAra = perKeyLvls[calcIDX].get(key);
					lvl = lvlAra[lvlAra.length-2];///keyLvlLen;
				} else { lvl = lvlIdx;	}
				turnOnKey[calcIDX][key] = (numFramesOn[calcIDX][key] == 0);//turning on this key, only do so when 
				//need to check past responses, to see if this response is "drifting" or legitimate	
				numFramesOn[calcIDX][key] = numFramesOn[calcIDX][key]+2 > frameWindow ? frameWindow : numFramesOn[calcIDX][key]+2;				
				if (//((null!= lastMap) && (lastMap.containsKey(key))) ||			//will put in adjacent keys if key was in last time but not as loud as this time
						//(!getIfNeighborInMap(tmpTestMap,true, key, lvl,calcIDX)
						(!getIfNeighborInMap(tmpTestMap,false, key, lvl,calcIDX)
							)) {//check to make sure we don't put any adjacent keys in 
					tmpTestMap.put(key, lvl);
					tmp.put(lvl, key);
				}
			}	
			//decay all keys
			for(int i =0;i<numFramesOn[calcIDX].length;++i) {
				if(numFramesOn[calcIDX][i] > 0) {				
					numFramesOn[calcIDX][i]-=1;
					turnOffKey[calcIDX][i] = (!turnOnKey[calcIDX][i] && (numFramesOn[calcIDX][i]==1));
				}			
			}
			//build intervals for whichever is active
			buildIntervals(calcIDX, animMillis);		
		}		
		melodyCandidates[calcIDX].put(curTimeFromStartSmpl++, tmp);	
	}//procPerRunRes()
	
	//function to build melodic intervals for each calc type
	//just compare loudest note now with loudest last note - if the same, then do nothing, otherwise build interval
	private Integer[] timeOfLastInterval = new Integer[] {-1,-1};
	private void buildIntervals(int calcIDX, int animMillis ) {
		Integer loudestNote = lvlsPerPKey[calcIDX].firstEntry().getValue();
		if(loudestNote == null) {
			pa.outStr2Scr("loudest note key is null");
			return;
		}
		if(lastLoudestNote[calcIDX] != loudestNote) {//notes have changed
			switch(intProc[calcIDX]) {
			case 0 :{//end of old interval, first note of new interval
				myNoteIntervalTuple oldInterval = null;
				if(timeOfLastInterval[calcIDX] != -1.0f) {
					oldInterval = intervals[calcIDX].get(timeOfLastInterval[calcIDX]);
					if(oldInterval!=null) {
						oldInterval.finishInterval(loudestNote,animMillis);//win.getPrivFlags(DancingBallWin.calcSingleFreq) ? dftResIDX : fftResIDX
//						if((win.getPrivFlags(DancingBallWin.playMP3Vis)) && (calcIDX == dftResIDX)) {
//							pa.outStr2Scr("Old Interval : " + oldInterval.toString() + " animMillis : "+animMillis+"|new loudest note : " + loudestNote );
//						}
					}
				}
				timeOfLastInterval[calcIDX]= animMillis;
				myNoteIntervalTuple tmp = new myNoteIntervalTuple(this, animMillis, loudestNote,oldInterval);
				intervals[calcIDX].put(animMillis, tmp);
				break;}
			case 1 : {//transition of interval				
				myNoteIntervalTuple tmp = intervals[calcIDX].get(timeOfLastInterval[calcIDX]);
				if(tmp!=null) {tmp.setFirstTransition(loudestNote, animMillis);				}
				else {pa.outStr2Scr("Most recent interval @" +timeOfLastInterval[calcIDX]+ "millis is NULL ERROR!");}
				break;}
			}
			intProc[calcIDX] = (intProc[calcIDX] + 1)%2;
		}
		//TODO base on keys turned on/off ?
//		for(int k=0;k<turnOnKey[calcIDX].length;++k) {
//			if(turnOnKey[calcIDX][k]) {//				
//			} else if(turnOffKey[calcIDX][k]) {//				
//			} 			
//		}
		lastLoudestNote[calcIDX] = loudestNote;
	}//buildIntervals
	
	
	//TODO allow for multiple instruments, espeically with regard to midi (specify by channel/instrument) as arrays
	private int curChan = 0;
	private void calcPianoKeyMappings(mySongHandler song, int animMillis) {
		//analyze frequencies of center notes of piano manually using DFT approx or via fft
		//toArray makes copy of mix buffer
		setPerRunRes(song.mixBuffer());		//set up dft and fft(clear map) for run
		//execute all preconfigured threads
		try {callDFTMapperFtrs = pa.th_exec.invokeAll(callDFTNoteMapper);for(Future<Boolean> f: callDFTMapperFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }
		song.setDFTMaxLvl(lvlsPerPKey[dftResIDX].firstKey());
		procPerRunRes(dftResIDX, animMillis);	
		//once dft threads are done, process fft
		//TODO Allow for multiple instruments by calling this repeatedly for each channel - specifically for midi
		song.getFwdFreqLevelsInHarmonicBands(dispPiano.pianoMinFreqsHarmonics, lvlsPerPKey[fftResIDX], perKeyLvls[fftResIDX],curKeyLvlIdx, curChan);
		procPerRunRes(fftResIDX, animMillis);		
	}//calcPianoKeyMappings
	
	
	//private static int timer = 0;
	//set process audio for each frame
	public boolean processAudioData(float animTimeMod) {
		boolean updateBall = false;
		if(!getFlags(audioLoadedIDX)) {return updateBall;}
		mySongHandler song = getCurrentClip();
		if(song.isPlaying()) {
			song.stepAudio();
			float[][] res ;
		
			//TODO need to find response to excite ball from either dft or fft
			boolean simWTapOrSendToBall = win.getPrivFlags(DancingBallWin.stimWithTapBeats) || win.getPrivFlags(DancingBallWin.sendAudioToBall);
			if(simWTapOrSendToBall || win.getPrivFlags(DancingBallWin.showZoneBandRes) 
					|| (win.getPrivFlags(DancingBallWin.showTapBeats) && !win.getPrivFlags(DancingBallWin.useHumanTapBeats) )) {
				res = song.getFwdZoneBandsFromAudio();
				bandRes = res[0];
				bandFreqs = res[1];
				//update ball's knowledge of bandRes
				updateBall = true;
				//ball.setFreqVals(bandRes);
			}
			//only perform if showing all bands eq
			if(win.getPrivFlags(DancingBallWin.showAllBandRes)) {
				res = song.getFwdBandsFromAudio();
				allBandsRes = res[0];
				allBandFreqs = res[1];		
			}		
			
			//analyze frequencies of center notes of piano manually using DFT approx or via fft
			calcPianoKeyMappings(song, pa.millis());
			
			//if we're showing beat detected and we're not using human tapped beats
			if (win.getPrivFlags(DancingBallWin.showTapBeats) || simWTapOrSendToBall){
				//copy current beats to lastbeat struct
				for (int i =0;i<lastBeatDetRes.length;++i) {lastBeatDetRes[i] = beatDetRes[i];}
				float animMillis = animTimeMod*1000;
				if (!win.getPrivFlags(DancingBallWin.useHumanTapBeats)) {//
					//true /false beat exists for all zones
					beatDetRes = song.beatDetectZones();
					//only add initial hit of beat as beat
					for (int i =0;i<beatDetRes.length;++i) {if((beatDetRes[i]) && (!lastBeatDetRes[i])) {	audioBeats[i].addTap(pa.millis()); }		}
				} else {//copy finger tap beats here
					//pa.outStr2Scr("copy tapbeats");
					for (int i =0;i<tapBeats.length;++i) {	
						tapBeats[i].simBeat(animMillis);
						beatDetRes[i]=tapBeats[i].beatIsOn;	
					}				
				}
				//pa.outStr2Scr("zone : 0 beat : " + beatDetRes[0]+" last beat det : " + lastBeatDetRes[0] );
			} else {//not using beats,clear out structs
				lastBeatDetRes = new boolean[numZones];
				beatDetRes = new boolean[numZones];
			}
			//pa.outStr2Scr("Song Pos : " + song.getPlayPos()+" | song length : " + song.songLength);
			if(song.donePlaying() ) {win.setPrivFlags(DancingBallWin.playMP3Vis, false);}//shut off songs if done
		}else {
			//if(win.getPrivFlags(DancingBallWin.playMP3Vis)){win.setPrivFlags(DancingBallWin.playMP3Vis, false);}
			lastBeatDetRes = new boolean[numZones];
			beatDetRes = new boolean[numZones];
			bandRes = new float[numZones];
			bandFreqs = new float[numZones];
			allBandsRes = new float[numZones];
			allBandFreqs = new float[numZones];
			initLvlMaps();
		}
		
		return updateBall;
	}//processAudioData
	

	//save the timing of a tapped beat of a certain type - map to zones for now
	//when this is called, key was pressed to signify the location of a beat of type key.
	//this will happen multiple times, and the average of the taps will represent the timing of the beat for type key
	public void saveTapBeat(int key) {
		if (win.getPrivFlags(DancingBallWin.useHumanTapBeats)) {tapBeats[key].addTap(pa.millis());} 
//		else {//if not enabled (not shown) then reset tapBeats struct
//			initTapBeatStructs();
//		}		
	}//saveTapBeat
	//draw bar representing level at a certain band
	private void drawFreqBands(float[] bandRes, float[] bandFreqs, float height, int clr, boolean drawBeats, boolean showFreqs) {
		pa.pushMatrix();pa.pushStyle();
		float transY =-2*height;
		float drawBeatsOffset = drawBeats ? 20 : 0, showFreqsOffset = showFreqs ? (height == 1) ? 180 : 40 : 0;
		pa.translate( 10 + drawBeatsOffset,  win.rectDim[3]+transY);
		float width = (win.rectDim[2]-(40 + drawBeatsOffset + showFreqsOffset)),
				//scale value of how wide to draw the actual data
				wdLogMult = width/6.0f;//,wdMult = width/80.0f;
		if(showFreqs) {
			float txtHt = .4f*height + 5.0f;
			pa.translate(showFreqsOffset,0,0);
			pa.pushMatrix();pa.pushStyle();
			pa.setStroke(win.strkClr);
			pa.textSize(txtHt);
			for (int i=0;i<bandRes.length;++i) {
				pa.pushMatrix();pa.pushStyle();
				//alternate display of freqs on either side of line
				if(height == 1) {pa.translate(-30 *(1 + (i%6)),0,0);} else {pa.translate(-showFreqsOffset,0,0);}// tiny bar do staggered translate
				pa.text(String.format("%5.1f", bandFreqs[i]), -3.0f, txtHt);
				//draw ctr freq name				
				pa.popStyle();pa.popMatrix();		
				pa.translate(0,transY);
			}
			pa.popStyle();pa.popMatrix();		
		}
		for (int i=0;i<bandRes.length;++i) {
			//draw freq bar
			if(height > 1) {//not tiny bar
				pa.noFill();
				pa.setStroke(win.strkClr);
				pa.rect(0,0,width, height);			
				pa.setColorValFill(clr);
			} else {//height is 1==tiny bar
				pa.setColorValFill(clr);
				if(i % 100 == 0) {pa.setColorValFill(pa.gui_White);}
			}
			pa.noStroke();
			//pa.rect(0,0,wdMult * bandRes[i], height);		
			pa.rect(0,0,wdLogMult * (float)Math.log1p(bandRes[i]), height);		
			pa.translate(0,transY);
		}		
		pa.popStyle();pa.popMatrix();		
	}//drawFreqBand
	
	//remove custom handling of tap beats - have them treated just like detected beats
//	//draw representations of beats on screen
//	private void drawBeats(myBeat[] beats, float modAmtMillis, float height) {
//		float rad = height/2.0f;
//		pa.pushMatrix();pa.pushStyle();
//		float transY =-2*height;
//		pa.translate( 10 + rad,  win.rectDim[3]+transY + rad);
//		for (int i=0;i<beats.length;++i) {			
//			beats[i].drawBeat(modAmtMillis, rad);
//			pa.translate(0,transY);
//		}
//		pa.popStyle();pa.popMatrix();	
//	}//drawTapBeats()
	
	//display beats detected in music
	private void drawDetectedBeats(boolean[] beatState, boolean[] lastBeatState, myBeat[] beats, float height) {
		float rad = height/2.0f;
		pa.pushMatrix();pa.pushStyle();
		pa.noStroke();
		float transY =-2*height;
		int clr;
		pa.translate(10 + rad,  win.rectDim[3]+transY + rad);
		for (int i=0;i<beatState.length;++i) {		//low freq == idx 0, high freq == idx beatState.length-1
			if (beatState[i]){	clr = pa.gui_Green; }
			else if (lastBeatState[i]) {clr = pa.gui_Red;}
			else {		clr=pa.gui_Gray;}//show beat on and determine if it should be turned off			
			//pa.show(myPointf.ZEROPT,height, clr, clr, true);
			pa.showFlat(myPointf.ZEROPT,height, clr, clr,pa.gui_White,String.format("%.4f", beats[i].getBeatFreq()));
			pa.translate(0,transY);
		}
		pa.popStyle();pa.popMatrix();	
	}//drawDetectedBeats	
	
	//get appropriate beat array
	public myBeat[] getBeats() {	return win.getPrivFlags(DancingBallWin.useHumanTapBeats) ? tapBeats : audioBeats;}
	//draw all results
	public void drawScreenData(float modAmtMillis) {
		pa.hint(PConstants.DISABLE_DEPTH_TEST);
		float bandResHeight = 10.0f;
		boolean showBeats = win.getPrivFlags(DancingBallWin.showTapBeats),
				showPianoNotes = win.getPrivFlags(DancingBallWin.showPianoKbd),
				showFreqlbls = win.getPrivFlags(DancingBallWin.showFreqLbls),
				showAllBandRes = win.getPrivFlags(DancingBallWin.showAllBandRes);
		if(showPianoNotes) {
			boolean showDFTRes = win.getPrivFlags(DancingBallWin.calcSingleFreq),
					showMelodyTrail = win.getPrivFlags(DancingBallWin.showMelodyTrail);

			if(getFlags(audioLoadedIDX)) {
				int barWidth = 400; //width of bar to draw
				
				int ftIDX = (showDFTRes ? 0 : 1);
				//draw band Res
				mySongHandler song = getCurrentClip();
				//need scale factor for bars so they don't go off screen, should be max level seen so far in song
				float scaleFactor = song.barDispMaxLvl[ftIDX];
				float maxLvl = lvlsPerPKey[ftIDX].size() == 0 ? 0 : lvlsPerPKey[ftIDX].firstKey();
				//TODO need to find appropriate way to consume this - when loud sections of song kick in, overpowers higher frequency parts
				//float minAudThres = audThreshold * maxLvl;
				//TODO : set to display or not, also set offset 
				//dispPiano.drawNumFramesOn(numFramesOn[ftIDX]);
				//dispPiano.drawKeyLvlVals(lvlsPerPKey[ftIDX]);
				int transForNum = 0;
				if((maxLvl >= pa.epsValCalc) && !(showAllBandRes)) {//don't show piano results if showing all band res
					if(showMelodyTrail) {dispPiano.drawMelodyCands(melodyCandidates[ftIDX], curTimeFromStartSmpl, win.rectDim[2], transForNum);}
					if(showDFTRes) {//use single frequency DFT mechanism
						if(dftThdResToShow == 2) {//win.getPrivFlags(DancingBallWin.showEachOctave)) {
							for(int i=0;i<perThLvlPerKey.length;++i) {
								float minAudThresPerThd = audThreshold * perThLvlPerKey[i].firstKey();
								dispPiano.drawPlayedNote(perThLvlPerKey[i], minAudThresPerThd, i, numNotesToShow);
								if(!showMelodyTrail){	dispPiano.drawPianoBandRes(perThLvlPerKey[i], scaleFactor,barWidth, i, transForNum);}
							}						
						} else if (dftThdResToShow == 1) { //perThLvlBandsPerKey
							for(int i=0;i<perThLvlBandsPerKey.length;++i) {
								float minAudThresPerBand = audThreshold * perThLvlBandsPerKey[i].firstKey();
								dispPiano.drawPlayedNote(perThLvlBandsPerKey[i], minAudThresPerBand, i, numNotesToShow);
								if(!showMelodyTrail){	dispPiano.drawPianoBandRes(perThLvlBandsPerKey[i],scaleFactor, barWidth, i, transForNum);}
							}
						} else {
							dispPiano.drawPlayedNote(lvlsPerPKey[ftIDX], 0.0f, 7, numNotesToShow);	
							if(!showMelodyTrail){//not showing melody trail - show per key levels
								dispPiano.drawPianoBandRes( lvlsPerPKey[ftIDX],scaleFactor, barWidth,  7, transForNum);
							} 					
						}//draw results for dft				
					} else {//showing fft res			
						dispPiano.drawPlayedNote(lvlsPerPKey[ftIDX], 0.0f ,6, numNotesToShow);
						if(!showMelodyTrail){//not showing melody trail - show per key levels
							dispPiano.drawPianoBandRes( lvlsPerPKey[ftIDX], scaleFactor,barWidth,  6, transForNum);
						} 				
					}//use FFT mechanism
				}
			}
		}		
		if (showAllBandRes) {//if showing all bands, displace by piano keys' width
			if(showPianoNotes) {//if showing piano notes, displace by piano keys' width
				pa.pushMatrix();pa.pushStyle();
				pa.translate(dispPiano.whiteKeyWidth,0,0);		
			}
			drawFreqBands(allBandsRes, allBandFreqs, 1.0f, pa.gui_TransRed, showBeats,showFreqlbls);
		}
		else if(win.getPrivFlags(DancingBallWin.showZoneBandRes)) {drawFreqBands(bandRes, bandFreqs, bandResHeight, pa.gui_Blue, showBeats, showFreqlbls);}
		if(showAllBandRes && showPianoNotes) {	pa.popStyle();pa.popMatrix();	}							//undo piano translation		
		if(showBeats) {	drawDetectedBeats(beatDetRes, lastBeatDetRes, getBeats(),  bandResHeight);}			//show beats if detecting them
		pa.hint(PConstants.ENABLE_DEPTH_TEST);		
	}//drawScreenData
	
}//class myAudioAnalyzer



//class to hold functionality for tapped beat
class myBeat{
	public DancingBalls pa;
	//# of taps used to determine window to average for beats
	public static int windowSize = 4;
	//time to display beat being on, in milllis
	private static final float beatTtlDispTime = 100.0f;
	//type of beat this represents (i.e. zone)
	public int type;
	//arrays holding beat abs time in millis and beat-to-beat time
	private int[] beats, beatTime;
	//current index in beats and beatTime
	private int beatIDX = 0;
	//running average of beat timing
	public float avgBeatTime, beatDispTime, curBeatTime;
	//this beat structure is ready to consume - only after windowSize elements have been processed
	public boolean ready, beatIsOn;
	//
	
	public myBeat(DancingBalls _p, int _type, int stTime) {
		pa = _p;type=_type;
		//time location of beat taps
		beats = new int[windowSize];
		for(int i=0;i<windowSize;++i) {beats[i]=stTime;}
		//time between taps
		beatTime = new int[windowSize];
		avgBeatTime = 0;		
		ready = false;
	}
	//keep a series of 4 beat times, updating each of 4 with subsequent additional info
//	public void addTapNew(int tapTime) {//TODO
//		
//	}
	
	//add timing of new tap, replace oldest entry, recalculate avgBeatTime (avg beat-to-beat timing)
	//tapTime should be in millis
	public void addTap(int tapTime) {
		beats[beatIDX] = tapTime;		//abs time of beat tap
		beatTime[beatIDX] = tapTime - beats[(beatIDX + windowSize - 1)%windowSize];//last beat time - get delta beat time
		beatIDX = (beatIDX + 1)%windowSize;
		if(beatIDX == 0) {//cycled around at least 1 time
			ready = true;
		}//once windowSize elements have been processed, this structure is ready
		if(ready) {//if ready to, calculate average
			avgBeatTime = 0;
			for(int i=0;i<beatTime.length;++i) {avgBeatTime += beatTime[i];}
			avgBeatTime /= beatTime.length;	
		}
	}//addTap
	//return frequency in hz of beats
	public float getBeatFreq() {
		if(avgBeatTime == 0) {return -1;}
		return 1000.0f/avgBeatTime;
	}
	public void simBeat(float animTime) {//animtime in millis?
		
		//NOTE beatTtlDispTime should never be longer than avgBeatTime
		curBeatTime += animTime;		//time between beats
		if(!ready) {
			//do nothing
		} else if (beatIsOn){//show beat on and determine if it should be turned off			
			beatDispTime += animTime;
			if(beatTtlDispTime < beatDispTime) {//check if displayed long enough
				beatDispTime = 0;
				beatIsOn = false;				
			}
		} else {
			//check if beat should be on
			if(avgBeatTime <= curBeatTime) {
				curBeatTime = 0;
				beatIsOn = true;				
			}	
		}
		//System.out.println("animtime : " + animTime + " ready " + ready +"|"+beatIsOn+"|beatDispTime:"+beatDispTime+"|curBeatTime:"+curBeatTime+"|avgBTime:"+avgBeatTime);
	}//simBeat
	
//	//draw if ready and animTime (time in seconds since last frame)
//	public void drawBeat(float animTime, float rad) {
//		pa.pushMatrix();				pa.pushStyle();	
//		pa.noStroke();
//		//NOTE beatTtlDispTime should never be longer than avgBeatTime
//		curBeatTime += animTime;		//time between beats
//		int clr;
//		if(!ready) {
//			clr = pa.gui_Red;
//		} else if (beatIsOn){//show beat on and determine if it should be turned off			
//			clr = pa.gui_Green;
//			beatDispTime += animTime;
//			if(beatTtlDispTime < beatDispTime) {//check if displayed long enough
//				beatDispTime = 0;
//				beatIsOn = false;				
//			}
//		} else {
//			clr=pa.gui_Gray;
//			//check if beat should be on
//			if(avgBeatTime <= curBeatTime) {
//				curBeatTime = 0;
//				beatIsOn = true;				
//			}	
//		}		
//		//System.out.println("animtime : " + animTime + " ready " + ready +"|"+beatIsOn+"|beatDispTime:"+beatDispTime+"|curBeatTime:"+curBeatTime+"|avgBTime:"+avgBeatTime);
//		pa.show(myPointf.ZEROPT,rad*2.0f, clr, clr, true);
//		pa.popStyle();					pa.popMatrix();		
//	}//drawBeat	
}//myBeat

