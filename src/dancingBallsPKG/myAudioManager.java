package dancingBallsPKG;

import java.util.*;
import java.util.concurrent.*;

import ddf.minim.*;
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
			fftLoadedIDX		= 1,
			audioLoadedIDX		= 2;
	public static final int numFlags = 3;
	
	//handled sample rates based on songs loaded - put sample rates in keys
	public ConcurrentSkipListMap<Float, Integer> sampleRates;

//	//minim audio-related variables
	//1 holds results from analysis - magnitude key, value is index of note with max level within min/max note bounds;
	//2 is result from analyzing 1st 8 frequencies of harmonic series for each piano note
	public ConcurrentSkipListMap<Float, Integer> lvlsPerPKeyFundFFT, lvlsPerPKeyDFTCalc;
	//result from analyzing frequencies, keyed by key, value == level for fft (1) and dft(2)
	public ConcurrentSkipListMap<Integer, Float> perKeyLevelsFFTCalc, perKeyLevelsDFTCalc;	
	//number of top signal level notes to show per display result (either globally or within thread results)
	public int numNotesToShow = 1;	
	//results from indiv analysis of all threads; perThLvlBandsPerKey is smaller array with aggregate results for subsets of threads
	public ConcurrentSkipListMap<Float, Integer>[] perThLvlPerKey, perThLvlBandsPerKey;	
	//one entry per analysis band - TODO reconfigure for per-song analysis - group each thread(which covers a frequency zone) depending on nature of song
	private int[][] perThLvlIDXs = new int[][] {{0,1,2},{3,4,5,6,7},{8,9}};
	
	//set by window UI selection
	// 0:global max, 1:freq zone max or 2:per-thread max note levels
	public int dftThdResToShow = 0;
	
	//structure holding multiple melody candidates - milllis from beginning as key, value is map of melody candidate keys at that time stamp, keyed by lvl
	public ConcurrentSkipListMap<Integer, ConcurrentSkipListMap<Float, Integer>> melodyCandidates;	
	
	//time when song starts playing in millis
	public int songStartMillis, pauseTimeMillis, curTimeFromStartMillis;
	//sample count from start of song
	public int curTimeFromStartSmpl;
	//
	public final int fftMinBandwidth = 20, fftBandsPerOctave = 24;
	//per zone avg frequencies
	public float[] //blankBands = new float[numZones], 
			bandRes = new float[numZones], bandFreqs = new float[numZones], 
			allBandsRes = new float[numZones], allBandFreqs = new float[numZones];
	public boolean[] beatDetRes = new boolean[numZones], lastBeatDetRes = new boolean[numZones];
	//threads for working on dft analysis
	public List<myDFTNoteMapper> callDFTNoteMapper;
	public List<Future<Boolean>> callDFTMapperFtrs;

	//per bank arrays of buffer size, song handlers, song file names
	//current song index and songBank (bank corresponds to songs or piano samples)
	//list of song banks - use to pick either songs or piano notes
	public static int songIDX = 0, songBank = 0;
	//threshold below which audio is ignored - fraction of max level seen
	public float audThreshold = .2f;
	public static String[] songBanks = new String[] {"Piano Songs", "Misc Songs", "Bach Cello", "Piano Notes"};
	//list of song names
	public static String[][] songList = new String[][]{
		{"WellTmprdClav CMaj","Sati-Gnoss1","Sati-Gymn1","Fur Elise"},
		{"PurpleHaze","UNATCO","Hunting","SavanaDance","Karelia","Choir"},
		{"Cello4 EbMaj","Cello5 Cmin"},
		{"ff-029","ff-030","ff-031","ff-050","ff-051","ff-052","ff-053","ff-054"}};
	//song buffer size on per-bank basis
	public final int[] songBufSize = new int[] {1024, 1024, 1024, 1024};//{1024, 2048, 2048, 1024};
	public myMP3SongHandler[][] songs;
	public String[][] songFilenames = new String[][]{
		{"WTK_Cmaj.mp3","satie_gnossienne1.mp3","satie_gymnopedie1.mp3","FurElise.mp3"},
		{"PurpleHaze.mp3","UNATCO.mp3","Hunting.mp3","SavanaDance.mp3","karelia.mp3","choir.mp3"},
		{"Bach cello No. 4 in EbMaj_Prelude.mp3","Bach cello No. 5 in CMin_Prelude.mp3"},
		{"piano-ff-029.wav","piano-ff-030.wav","piano-ff-031.wav","piano-ff-050.wav","piano-ff-051.wav","piano-ff-052.wav","piano-ff-053.wav","piano-ff-054.wav"}
	};
	//whether to use piano tuning or equal temperment tuning
	public boolean[][] usePianoTune = new boolean [][]{
		{true, true, true, true},
		{false,  false, false, false, false, false},
		{false, false},
		{true, true, true, true, true, true, true, true}
		};
		
	//list of intervals found in current song
	public ConcurrentSkipListMap<Float, myNoteIntervalTuple> intervals;
	
	
	//current index of fft windowing function, from ui
	public int curWindowIDX = 0;	
	public WindowFunction[] windowList = new WindowFunction[]{FFT.NONE, FFT.BARTLETT, FFT.BARTLETTHANN, FFT.BLACKMAN, FFT.COSINE, FFT.GAUSS, FFT.HAMMING, FFT.HANN, FFT.LANCZOS, FFT.TRIANGULAR};
	
	//beat interaction
	public myBeat[] tapBeats, audioBeats;

	public myAudioManager(DancingBalls _pa,DancingBallWin _win) {
		pa=_pa; win=_win;dispPiano = win.dispPiano;
		initMe();		
	}//myAudioManager
	
	private void initMe() {
		initFlags();
		songs = new myMP3SongHandler[songBanks.length][];
		//ConcurrentSkipListMap<Float, Integer> allFreqsUsed = 
		dispPiano.initPianoFreqs();
		//initialize tap beat structures
		initTapBeatStructs();
		//load all songs, add sample rate to 
		loadSongsAndFFT();		
		//launch thread to precalculate all trig stuff : not needed with multi-threading dft calc - math is fast enough without this
		//pa.th_exec.execute(new myTrigPrecalc(this, allFreqsUsed) );
		
		//build DFT threads and precalc local cos/sin values
		initDFTAnalysisThrds(10);	
		changeCurrentSong(songBank, songIDX);
	}//initMe
	
	private void initFlags() {flags = new int[1 + numFlags/32]; for(int i = 0; i<numFlags; ++i){setFlags(i,false);}}
	public boolean getFlags(int idx){int bitLoc = 1<<(idx%32);return (flags[idx/32] & bitLoc) == bitLoc;}		
	public void setFlags(int idx, boolean val) {
		boolean curVal = getFlags(idx);
		if(val == curVal) {return;}
		int flIDX = idx/32, mask = 1<<(idx%32);
		flags[flIDX] = (val ?  flags[flIDX] | mask : flags[flIDX] & ~mask);
		switch(idx){
		case debugIDX		: {break;}
		case fftLoadedIDX	: {break;}
		case audioLoadedIDX	: {break;}		
		}
		
	}//setFlags
	
	//(re) initialize song analysis maps
	public void initLvlMaps(){
		for(int i=0;i<perThLvlPerKey.length;++i) {	perThLvlPerKey[i].clear();	}
		for(int i=0;i<perThLvlBandsPerKey.length;++i) {perThLvlBandsPerKey[i].clear();}
		lvlsPerPKeyDFTCalc.clear();
		perKeyLevelsDFTCalc.clear();
		
	}//initLvlMaps
	
	//initialize array of mybeat to hold results of tapped beat data
	protected void initTapBeatStructs() {
		//beat holding array
		tapBeats = new myBeat[numZones];
		audioBeats = new myBeat[numZones];
		int stTime = pa.millis();
		for(int i=0;i<numZones; ++i) {	tapBeats[i] = new myBeat(pa,i,stTime);	audioBeats[i] = new myBeat(pa,i,stTime);	}		
	}//initTapBeatStructs
	
	private myMP3SongHandler getCurrentClip() {return songs[songBank][songIDX];}
	//either returns current song or current piano clip
	protected myMP3SongHandler getCurrentClip(int bankIdx, int songIdx) {return songs[bankIdx][songIdx];}
	
	protected void setFFTVals() {
		for(int b=0;b<songBanks.length;++b) {
			for(int i=0;i<songs[b].length;++i){songs[b][i].setFFTVals(windowList[curWindowIDX], fftMinBandwidth, fftBandsPerOctave, numZones);}
		}
	}//setFFTVals
	
	protected void loadSongsAndFFT() {
		sampleRates = new ConcurrentSkipListMap<Float, Integer>();//hold only sample rates that we have seen
		for(int b=0;b<songBanks.length;++b) {
			myMP3SongHandler[] tmpSongs = new myMP3SongHandler[songFilenames[b].length];
			for(int i=0;i<songFilenames[b].length;++i){	
				tmpSongs[i]= new myMP3SongHandler(pa.minim, 
					songFilenames[b][i], 
					songList[b][i], 
					songBufSize[b]);	
				sampleRates.put(tmpSongs[i].playMe.sampleRate(), 1);}		
			songs[b] = tmpSongs;
		}
		setFlags(audioLoadedIDX,true);
		setFlags(fftLoadedIDX, true);
		setFFTVals();
	}//loadSongList() 
	
	public void changeCurrentSong(int newSongBank, int newSongIDX){
		getCurrentClip(songBank, songIDX).pause();//pause current song
		//ball.resetVertLocs();
		songBank = (newSongBank % songs.length);
		songIDX = (newSongIDX % songs[songBank].length);
		//change per-thread values since song has changed
		setDFTRunValsForCurSong();
		
		if(win.getPrivFlags(DancingBallWin.playMP3Vis)){startAudio();}
	}//changeCurrentSong
	
	public void changeCurrentWindowfunc(int newWinFuncIDX) {
		curWindowIDX = newWinFuncIDX;
		if(getFlags(fftLoadedIDX)) {setFFTVals();}
	}//changeCurrentWindowfunc

	public void startAudio(){
		if(!getFlags(audioLoadedIDX)){loadSongsAndFFT();}//load songs if not loaded already
		//pa.outStr2Scr("Song in buffer : " + songTitles[songIDX] + " size: " +  songs[songIDX].bufferSize() + " Sample rate : "+ songs[songIDX].sampleRate());
		//set time of start of song as now
		songStartMillis = pa.millis();
		curTimeFromStartSmpl = 0;
		melodyCandidates.clear();
		//need to reinitialize analysis results structures
		initLvlMaps();
		getCurrentClip(songBank,songIDX).play();
	}//startAudio
	
	

	public void pauseAudio(){		if(getFlags(audioLoadedIDX)){			stopAllPlaying();	}}
	//stop all clips from playing
	protected void stopAllPlaying() {
		pauseTimeMillis = pa.millis();
		for(int b=0;b<songs.length;++b){for(int i=0;i<songs[b].length;++i){	songs[b][i].pause();	}	}	
	}
	//rewind current song
	public void rewindSong() {if(win.getPrivFlags(DancingBallWin.playMP3Vis)){getCurrentClip(songBank,songIDX).rewind();}}
	
	//convenience function to build descending-sorted maps
	public ConcurrentSkipListMap<Float, Integer> buildDescMap(){return new ConcurrentSkipListMap<Float, Integer>(new Comparator<Float>() { @Override public int compare(Float o1, Float o2) {   return o2.compareTo(o1);}});}
	
	//init values used by dft
	public void initDFTAnalysisThrds(int numThreads) {
		//threads 0-3 are bass range
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
		lvlsPerPKeyDFTCalc = buildDescMap();
		lvlsPerPKeyFundFFT = buildDescMap();
		
		perKeyLevelsDFTCalc = new ConcurrentSkipListMap<Integer, Float>();
		perKeyLevelsFFTCalc = new ConcurrentSkipListMap<Integer, Float>();
		
		//melody candidates - a map of key/level tuples for each candidate, indexed by song time 
		//basically the top n candidates at every buffer sample
		melodyCandidates = new ConcurrentSkipListMap<Integer, ConcurrentSkipListMap<Float, Integer>>(new Comparator<Integer>() { @Override public int compare(Integer o1, Integer o2) {   return o2.compareTo(o1);}});
		
		perThLvlPerKey = new ConcurrentSkipListMap[numThreads];
		perThLvlBandsPerKey = new ConcurrentSkipListMap[perThLvlIDXs.length];
		for(int i=0;i<perThLvlPerKey.length;++i) {	perThLvlPerKey[i] = buildDescMap();	}
		for(int i=0;i<perThLvlBandsPerKey.length;++i) {	perThLvlBandsPerKey[i] = buildDescMap();	}
	}

	//set song-dependent values in each thread when song changes
	private void setDFTRunValsForCurSong() {
		myMP3SongHandler song = getCurrentClip(songBank,songIDX);
		for(int i=0;i<perThLvlPerKey.length;++i) {
			callDFTNoteMapper.get(i).setPerSongValues(song.playMe.sampleRate(),songBufSize[songBank], 2);// (i < 4 ? 2 : 1));
		}
	}//setDFTRunValsForCurSong
	
	
	//set every time this is run before execution
	private void setPerRunRes(float[] _buffer) {
		initLvlMaps();
		//threads 0-3 are bass range
		//4-6 are mid range
		//7-9 are treble range.  
		boolean usePianoTemp = usePianoTune[songBank][songIDX];
		curTimeFromStartMillis = pa.millis() - songStartMillis;
		for(int i=0;i<perThLvlPerKey.length;++i) {callDFTNoteMapper.get(i).setPerRunValues(_buffer, usePianoTemp, lvlsPerPKeyDFTCalc,perKeyLevelsDFTCalc, perThLvlPerKey[i]);}
		//set values for "zone" collections of thread bands
		for(int i=0;i<perThLvlBandsPerKey.length;++i) {
			for(int j=0;j<perThLvlIDXs[i].length;++j) {   callDFTNoteMapper.get(perThLvlIDXs[i][j]).setPerRunSharedMap(perThLvlBandsPerKey[i]);}
		}
	}//setPerRunRes
	
	//check if neigbhor to particular piano key is already in map, or check if neighbor is in map but value is less (for last time)
	private boolean getIfNeighborInMap(ConcurrentSkipListMap<Integer, Float> lastMap, boolean chkLvl, Integer key, Float keyLvl) {
		int chkBnds = 2;
		if(lastMap == null) {return false;}
		if(!chkLvl) {//just check membership - see if neighbors present
			for(int i=-chkBnds;i<=chkBnds;++i) {
				if(i==0) {continue;}
				if(lastMap.keySet().contains(key+i)) {return true;}
			}
		} else {//check if key's neighbors in map, and if so, check if key Lvl > than neighbor level
			for(int i=-chkBnds;i<=chkBnds;++i){
				if(0==i)  {continue;}
				Float lvl = lastMap.get(key+i);
				if(lvl == null) {continue;}//no value present
				if(lvl > keyLvl) {//means neighbor is in last map and this neighbor's level is greater than key's keyLvl - can ignore this key
					return true;
				}
			}
		}		
		return false;
	}//getIfNeighborInMap
	
	private ConcurrentSkipListMap<Integer, Float>tmpTestMap, lastTmpTestMap;
	//process the level results of key values after each multi-thread run
	private void procPerRunRes(ConcurrentSkipListMap<Float, Integer> lvlToKeyMap) {
		int numMelodyCandidates = 5;

		//temp map to hold all keys that have been added, as keys, to test for locality
		tmpTestMap = new ConcurrentSkipListMap<Integer, Float>();
		//set current time and put top x candidates for melody in array
		//melodyCandidates
		ConcurrentSkipListMap<Float, Integer> tmp = buildDescMap(), 
				lastMap = melodyCandidates.size() == 0 ? null : melodyCandidates.firstEntry().getValue() ;
		float minAudThres = audThreshold * lvlToKeyMap.firstKey();
		int idx = 0;
		for(Float lvl : lvlToKeyMap.keySet()) {//descending order
			if(lvl <= minAudThres) {break;}
			Integer key = lvlToKeyMap.get(lvl);//key producing this particular lvl of response
//			if (//((null!= lastMap) && (lastMap.containsKey(key))) ||			//will put in adjacent keys if key was in last time but not as loud as this time
//				(!getIfNeighborInMap(tmpTestMap,false, key, lvl)
//						)) {//check to make sure we don't put any adjacent keys in 
				tmpTestMap.put(key, lvl);
				tmp.put(lvl, key);
				++idx;
				if(idx >= numMelodyCandidates) {break;}
			//}
		}
		lastTmpTestMap = tmpTestMap;
		//melodyCandidates.put(curTimeFromStartMillis, tmp);		
		melodyCandidates.put(curTimeFromStartSmpl++, tmp);		
	}//procPerRunRes()
	
	//private static int timer = 0;
	//set process audio for each frame
	public boolean processAudioData(float animTimeMod) {
		boolean updateBall = false;
		myMP3SongHandler song = getCurrentClip(songBank,songIDX);
		//songs[songIDX].fftFwdOnAudio();
		song.fftFwdOnAudio();
		float[][] res ;
		//artifact from jcuda implementation
//		res = song.fftSpectrumFromAudio(); // real and imaginary components of frequency from sample window
		//all zones - only perform if showing zone bands or ball is receiving audio, allowing for ball to receive stim from human tap beats TODO
		//get zone audio
		boolean simWTapOrSendToBall = win.getPrivFlags(DancingBallWin.stimWithTapBeats) || win.getPrivFlags(DancingBallWin.sendAudioToBall);
		if(simWTapOrSendToBall || win.getPrivFlags(DancingBallWin.showZoneBandRes) 
				|| (win.getPrivFlags(DancingBallWin.showTapBeats) && !win.getPrivFlags(DancingBallWin.useHumanTapBeats) )) {
			res = song.fftFwdNumBandsFromAudio();
			bandRes = res[0];
			bandFreqs = res[1];
			//update ball's knowledge of bandRes
			updateBall = true;
			//ball.setFreqVals(bandRes);
		}
		//only perform if showing all bands eq
		if(win.getPrivFlags(DancingBallWin.showAllBandRes)) {
			res = song.fftFwdBandsFromAudio();
			allBandsRes = res[0];
			allBandFreqs = res[1];		
		}		
		//analyze frequencies of center notes of piano manually using DFT approx
		if(win.getPrivFlags(DancingBallWin.calcSingleFreq)) {
//			if (timer % 2 == 0) {		
//				timer = 0;
			//toArray makes copy of mix buffer
			setPerRunRes(song.playMe.mix.toArray());	//send updates to dftAnalyzer
			try {callDFTMapperFtrs = pa.th_exec.invokeAll(callDFTNoteMapper);for(Future<Boolean> f: callDFTMapperFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }
			song.setDFTMaxLvl(lvlsPerPKeyDFTCalc.firstKey());
			procPerRunRes(lvlsPerPKeyDFTCalc);
//			} else {
//			++timer;
//			}
		}		
		if(win.getPrivFlags(DancingBallWin.showPianoNotes)) {
			song.fftFwdFreqLevelsInHarmonicBands(dispPiano.pianoMinFreqsHarmonics, lvlsPerPKeyFundFFT, perKeyLevelsFFTCalc);
			procPerRunRes(lvlsPerPKeyFundFFT);
		}
		//if we're showing beat detected and we're not using human tapped beats
		if (win.getPrivFlags(DancingBallWin.showTapBeats) || simWTapOrSendToBall){
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
		} else {//copy finger tap beats here
			lastBeatDetRes = new boolean[numZones];
			beatDetRes = new boolean[numZones];
		}
		if(song.getPlayPosRatio() > .99) {win.setPrivFlags(DancingBallWin.playMP3Vis, false);}//shut off songs if done
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
				showPianoNotes = win.getPrivFlags(DancingBallWin.showPianoNotes),
				showFreqlbls = win.getPrivFlags(DancingBallWin.showFreqLbls),
				showDFTRes = win.getPrivFlags(DancingBallWin.calcSingleFreq),
				showMelodyTrail = win.getPrivFlags(DancingBallWin.showMelodyTrail),
				showAllBandRes = win.getPrivFlags(DancingBallWin.showAllBandRes);
		if(showPianoNotes) {
			dispPiano.drawMe();
			int barWidth = 200; //width of bar to draw
			//draw band Res
			myMP3SongHandler song = getCurrentClip();
			//need scale factor for bars so they don't go off screen, should be max level seen so far in song
			if(showDFTRes) {//use single frequency DFT mechanism
				float maxLvl = lvlsPerPKeyDFTCalc.firstKey();
				float scaleFactor = song.barDispMaxLvl[0];
				if(maxLvl >= pa.epsValCalc) {
					if(showMelodyTrail) {dispPiano.drawMelodyCands(melodyCandidates, curTimeFromStartSmpl, win.rectDim[2]);}
					float minAudThres = audThreshold * maxLvl;
					if(dftThdResToShow == 2) {//win.getPrivFlags(DancingBallWin.showEachOctave)) {
						for(int i=0;i<perThLvlPerKey.length;++i) {
							//float minAudThres = audThreshold * perThLvlPerKey[i].firstKey();
							dispPiano.drawPlayedNote(perThLvlPerKey[i], minAudThres, i, numNotesToShow);
							if(!showMelodyTrail){	dispPiano.drawPianoBandRes(perThLvlPerKey[i], scaleFactor,barWidth, i);}
						}
						
					} else if (dftThdResToShow == 1) { //perThLvlBandsPerKey
						//float minAudThres = audThreshold * maxLvl;
						for(int i=0;i<perThLvlBandsPerKey.length;++i) {
							//float minAudThres = audThreshold * perThLvlPerKey[i].firstKey();
							dispPiano.drawPlayedNote(perThLvlBandsPerKey[i], minAudThres, i, numNotesToShow);
							if(!showMelodyTrail){	dispPiano.drawPianoBandRes(perThLvlBandsPerKey[i],scaleFactor, barWidth, i);}
						}
					} else {
						//TODO need to find appropriate way to consume this - when loud sections of song kick in, overpowers higher frequency parts
						//float minAudThres = audThreshold * maxLvl;
						dispPiano.drawPlayedNote(lvlsPerPKeyDFTCalc, 0.0f, 7, numNotesToShow);	
						if(!showMelodyTrail){//not showing melody trail - show per key levels
							dispPiano.drawPianoBandRes( lvlsPerPKeyDFTCalc,scaleFactor, barWidth,  7);
						} 					
					}//draw results for dft
				}
			} else {//showing fft res
				float maxLvl = lvlsPerPKeyFundFFT.firstKey();
				float scaleFactor = song.barDispMaxLvl[1];
				if(maxLvl >= pa.epsValCalc) {
					float minAudThres = audThreshold * maxLvl;
					dispPiano.drawPlayedNote(lvlsPerPKeyFundFFT, 0.0f ,6, numNotesToShow);
					if(!showMelodyTrail){//not showing melody trail - show per key levels
						dispPiano.drawPianoBandRes( lvlsPerPKeyFundFFT, scaleFactor,barWidth,  6);
					} else {
						dispPiano.drawMelodyCands(melodyCandidates, curTimeFromStartSmpl, win.rectDim[2]);
					}
				}
			}//use FFT mechanism
		}		
		if (showAllBandRes) {//if showing all bands, displace by piano keys' width
			if(showPianoNotes) {//if showing piano notes, displace by piano keys' width
				pa.pushMatrix();pa.pushStyle();
				pa.translate(win.whiteKeyWidth,0,0);		
			}
			drawFreqBands(allBandsRes, allBandFreqs, 1.0f, pa.gui_TransRed, showBeats,showFreqlbls);
		}
		else if(win.getPrivFlags(DancingBallWin.showZoneBandRes)) {drawFreqBands(bandRes, bandFreqs, bandResHeight, pa.gui_Blue, showBeats, showFreqlbls);}
		if(showBeats) {
			drawDetectedBeats(beatDetRes, lastBeatDetRes, getBeats(),  bandResHeight);
		}
		if(showAllBandRes && showPianoNotes) {	pa.popStyle();pa.popMatrix();	}		//undo piano translation		
		pa.hint(PConstants.ENABLE_DEPTH_TEST);		
	}//drawScreenData
	
}//class myAudioAnalyzer


//handles an mp3 song, along with transport control
class myMP3SongHandler{	
	public AudioPlayer playMe;	

	//beat detection stuff
	private int sensitivity, insertAt;
	private boolean[] fIsOnset;
	private float[][] feBuffer, fdBuffer;
	private long[] fTimer;
	
	public FFT fft;
	public String fileName, dispName;
	public int songBufSize;		//must be pwr of 2
	//length in millis, minimum frequency in fft, # of zones to map energy to
	public int songLength, minFreqBand, numZones;
	//start frequencies, middle frequencies, and end frequencies for each of numbands bands
	private float[] stFreqs, endFreqs, midFreqs;
	//public static final float[] FreqCtrs = new float[] {};//10 bands : 22Hz to 22KHz
	public int[] stFreqIDX, endFreqIDX;
	//max level seen so far - idx 0 is dft, idx1 is fft
	public float[] barDispMaxLvl;
	/**
	 * build song handler
	 * @param _minim ref to minim library object, to load song
	 * @param _fname song name
	 * @param _dname name to display
	 * @param _sbufSize song buffer size
	 */
	public myMP3SongHandler(Minim _minim, String _fname, String _dname, int _sbufSize) {
		fileName = _fname; dispName = _dname;songBufSize = _sbufSize;
		playMe = _minim.loadFile(fileName, songBufSize);
		songLength = playMe.length();
		//playMe.sampleRate()
		fft = new FFT(playMe.bufferSize(), playMe.sampleRate() );
		//beat = new myBeatDetect(playMe.bufferSize(), playMe.sampleRate());//, fft);
		insertAt = 0;
		sensitivity = 10;	
		barDispMaxLvl = new float[2];		barDispMaxLvl[0]=.01f;barDispMaxLvl[1]=.01f;
		//System.out.println("Song: " + dispName + " sample rate : " + playMe.sampleRate());
	}	
	
	//set up precomputed frequency arrays for each zone (6)
	private void setupFreqAras() {
		stFreqs = new float[numZones];	endFreqs = new float[numZones];	midFreqs = new float[numZones];
		stFreqIDX = new int[numZones]; endFreqIDX = new int[numZones];
		float maxFreqMult = 9.9f;//max multiplier to minFreqBand to consider
		//multiplier for frequencies to span minFreqBand * ( 1.. maxFreqMult) ==> 2 ^ maxFreqMult/numZones
		float perFreqMult = (float) Math.pow(2.0, maxFreqMult/numZones);
		stFreqs[0] = minFreqBand;
		for(int i=0; i<numZones;++i) {
			endFreqs[i] = stFreqs[i] * perFreqMult;
			stFreqIDX[i] = fft.freqToIndex(stFreqs[i]);
			endFreqIDX[i] = fft.freqToIndex(endFreqs[i]);
			if(i<numZones-1) {//set up next one
				stFreqs[i+1] = endFreqs[i]; 
			}
			midFreqs[i]= .5f *( stFreqs[i] + endFreqs[i]);
		}
	}
	
	//set values required for fft calcs.	
	public void setFFTVals(WindowFunction win, int fftMinBandwidth, int fftBandsPerOctave, int _numZones) {
		numZones = _numZones;//number of zones for dancing ball (6)
		minFreqBand = fftMinBandwidth;//minimum frequency to consider (== minimum bandwidth fft is required to handle)
		setupFreqAras();
		barDispMaxLvl = new float[2];		barDispMaxLvl[0]=.01f;barDispMaxLvl[1]=.01f;		
		//these are used for beat detection in each zone
		fIsOnset = new boolean[numZones];		
		int tmpAraSiz = (int) (playMe.sampleRate() / playMe.bufferSize());
		feBuffer = new float[numZones][tmpAraSiz];
		fdBuffer = new float[numZones][tmpAraSiz];
		fTimer = new long[numZones];
		long start = System.currentTimeMillis();
		for (int i = 0; i < fTimer.length; i++)	{
			fTimer[i] = start;
		}
		//set fft windowing function
		fft.window(win);
		//set fft to not calculate any averages
		fft.noAverages();
	}//setFFTVals

	//go through entire song, find ideal center frequencies and ranges for each of numZones zone based on energy content
	//split frequency spectrum into 3 areas ("bass", "mid" and "treble"), find numzones/3 ctr frequencies in each of 3 bands
	//# zones should be %3 == 0
	//this should be saved to disk so not repeatedly performed
	private void setZoneFreqs() {
		//first analyze all levels in entire song
		
		//next group frequencies
		
		
	}
	//call every frame before any fft analysis - steps fft forward over a single batch of samples
	public void fftFwdOnAudio() {fft.forward( playMe.mix ); }
//	//call to get data for fft display - call before any fft analysis
//	public float[][] fftSpectrumFromAudio() {	return new float[][] {fft.getSpectrumReal(), fft.getSpectrumImaginary()};}	
	//get levels of all bands in spectrum
	public float[][] fftFwdBandsFromAudio() {
		int specSize = fft.specSize();	//should be songBufSize / 2 + 1 : approx 512 bands
		float[] bandRes = new float[specSize], bandFreq = new float[specSize];
		for(int i=0;i<specSize;++i) {	
			bandFreq[i] = fft.indexToFreq(i);
			bandRes[i] = fft.getBand(i);
		}
		return new float[][] {bandRes,bandFreq};
	}//fftFwdBandsFromAudio	

//	//return a sorted map keyed by level of piano note idxs present in audio in each band of passed array of freq bands
//	//bounds array holds bounds within which to avg levels - idx i is low bound, i+1 is high bound
//	public ConcurrentSkipListMap<Float, Integer> fftFwdFreqLevelsInBands(float[] boundsAra){
//		ConcurrentSkipListMap<Float, Integer> res = new ConcurrentSkipListMap<Float, Integer>(new Comparator<Float>() {
//          @Override
//          public int compare(Float o1, Float o2) {   return o2.compareTo(o1);}
//      });
//		for (int i=0;i<boundsAra.length-1; ++i) {res.put(fft.calcAvg(boundsAra[i], boundsAra[i+1]), i);}		
//		return res;
//	}//fftFwdFreqLevelsInBands
	
	/**
	 * analyze 1st 8 frequencies of harmonic series for each piano note
	 * @param harmSeries 
	 * @param boundsAra array per key of min frequencies of each key's fundamental and harmonic
	 */
	public void fftFwdFreqLevelsInHarmonicBands(float[][] keyMinAra, ConcurrentSkipListMap<Float, Integer> res1, ConcurrentSkipListMap<Integer, Float> res2){
		res1.clear();
		res2.clear();
		float[] keyLoudness = new float[keyMinAra.length-1];		
		//boundsara holds boundaries of min/max freqs for each key
		for (int key=0;key<keyMinAra.length-1; ++key) {keyLoudness[key] = fft.calcAvg(keyMinAra[key][0], keyMinAra[key+1][0]);}		
		
		for (int i=0;i<keyMinAra.length-1; ++i) {
			float freqLvl=0;
			for (int h=0; h < keyMinAra[i].length;++h) {freqLvl += fft.calcAvg(keyMinAra[i][h], keyMinAra[i+1][h]);}// fft.getFreq(harmSeries[i][h]);
			freqLvl *= keyLoudness[i]/keyMinAra[i].length;		//weighting by main key level
			res1.put(freqLvl, i);
			res2.put(i, freqLvl);
		}		
		float maxLvl = res1.firstKey();		
		barDispMaxLvl[1] = (barDispMaxLvl[1] < maxLvl ? maxLvl : barDispMaxLvl[1]);
	}//fftFwdFreqLevelsInHarmonicBands
	
	//set max level for this song seen so far from dft
	public void setDFTMaxLvl(float maxLvl) {
		barDispMaxLvl[0] = (barDispMaxLvl[0] < maxLvl ? maxLvl : barDispMaxLvl[0]);		
	}
	
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

	//returns all spectrum results averaged into numZones bands
	//only use numBands divs of first specSize/2 frequencies
	public float[][] fftFwdNumBandsFromAudio() {
		float[] bandRes = new float[numZones], bandFreq = new float[numZones];
		for(int i=0; i<numZones;++i) {
			bandRes[i] = fft.calcAvg(stFreqs[i], endFreqs[i]);
			bandFreq[i] = midFreqs[i];
		}
		beatDetect(bandRes);
		return new float[][] {bandRes,bandFreq};
	}//fftFwdNumBandsFromAudio
	
	//check this to see if beat has been detected
	public boolean[] beatDetectZones() {
		boolean[] retVal = new boolean[fIsOnset.length];
		for(int i=0;i<fIsOnset.length;++i) {retVal[i]=fIsOnset[i];}
		return retVal;
	}
	
	private float calcMean(float[] ara){
		float avg =  ara[0];
		for (int i = 1; i < ara.length; ++i){avg += ara[i];}
		avg /= ara.length;
		return avg;
	}

	private float specAverage(float[] arr){
		float avg = 0,num = 0;
		for (int i = 0; i < arr.length; ++i){	if (arr[i] > 0)	{avg += arr[i];++num;}	}
		if (num > 0){avg /= num;}
		return avg;
	}

	private float calcVar(float[] arr, float val){
		float V = (float)Math.pow(arr[0] - val, 2);
		for (int i = 1; i < arr.length; ++i){V += (float)Math.pow(arr[i] - val, 2);}
		V /= arr.length;
		return V;
	}
	
	//song control
	public void play() {	playMe.play();}
	public void play(int millis) {	if(millis>=songLength) playMe.play(); else playMe.play(millis);}
	public void pause() {	playMe.pause(); if (getPlayPosRatio() >= .99f ) {playMe.rewind();}}
	public void rewind() {  playMe.rewind();}
	//change current playing location
	public void modPlayLoc(float modAmt) {
		int curPos = playMe.position();	
		int dispSize = songLength/20, newPos = (int) (curPos + (dispSize * modAmt));
		if(newPos < 0) { newPos = 0;} else if (newPos > songLength-1){newPos = songLength-1;}
		playMe.cue(newPos);
	}	
	//get position in current song
	public int getPlayPos() {return playMe.position();}
	//get fraction of completion of current location in song
	public float getPlayPosRatio() {return playMe.position()/(1.0f*songLength);}
}//myMP3SongHandler

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

