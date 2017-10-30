package dancingBallsPKG;

import processing.core.PConstants;
//
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Future;

//
import ddf.minim.analysis.*;
import ddf.minim.*;

public class DancingBallWin extends myDispWindow {
	//set # zones here
	public static final int numZones = 6;	
	//Dancing Ball object
	public myDancingBall ball;
	//piano visualization object
	public myPianoObj dispPiano;
	
	//audio manager - move all audio processing to this object
	public myAudioManager audMgr;
	
	///////////
	//ui vals
	//sim timestep from ui
	public float deltaT = .01f;
	//# of verts for dancing ball, approx - divided up for snowman based on radius of snowman
	public int ballNumVerts = 2000;
	//default radius of ball - for snowman scaled for each ball
	public float ballRadius = 200;
	//minimum fraction of vert count to belong in a neighborhood - if the derived # of verts for a particular zone is less than this, it will be forced to be this many
	public int minVInNBD = 10;
	//zone to display when displaying zones on sphere
	public int zoneToShow = 0;
	//zone member to show
	public int zoneMmbrToShow = 0;
	//current song index
	//public int songIDX = 1;
	//current index of fft windowing function, from ui
	public int curWindowIDX = 0;
	//idxs - need one per object
	public final static int
		gIDX_TimeStep 		= 0,
		gIDX_NumVerts		= 1,
		gIDX_BallRad		= 2,
		gIDX_minVertNBHD	= 3,
		gIDX_zoneToShow		= 4,
		gIDX_zoneMmbrToShow = 5,
		gIDX_curSong 		= 6,
		gIDX_winSel 		= 7;
	//initial values - need one per object
	public float[] uiVals = new float[]{
			deltaT,
			ballNumVerts,
			ballRadius,
			minVInNBD,
			zoneToShow,
			zoneMmbrToShow,
			1,//songIDX,
			curWindowIDX			
	};			//values of 8 ui-controlled quantities

	public final int numGUIObjs = uiVals.length;											//# of gui objects for ui
	
	public float timeStepMult = 1.0f;													//multiplier to modify timestep to make up for lag
	
//	//handled sample rates based on songs loaded - put sample rates in keys
//	public ConcurrentSkipListMap<Float, Integer> sampleRates;
//	//precalced trig for multiple sample rates, each holding results of trig functions on 2pi * freq * t/ sampleSize ->value
//	//public ConcurrentSkipListMap<Float, ConcurrentSkipListMap<Float, Float[]>> cosTbl, sinTbl;
//		
//	//holds results from analysis - magnitude key, value is index of note with max level within min/max note bounds
//	public ConcurrentSkipListMap<Float, Integer> levelsPerPianoKeyFund;
//	//result from analyzing 1st 8 frequencies of harmonic series for each piano note
//	public ConcurrentSkipListMap<Float, Integer> levelsPerPKeySingleCalc;
//	//results from analysis in the bass, mid and trbl ranges (4 octaves, 3 octaves, 3 octaves
//	public ConcurrentSkipListMap<Float, Integer> bassLvlsPerKey,midLvlsPerKey,trblLvlsPerKey;
	
	//private child-class flags - window specific
	public static final int 
			debugAnimIDX 		= 0,						//debug
			modDelT				= 1,			//whether to modify delT based on frame rate or keep it fixed (to fight lag)
			usePianoNoteFiles   = 2,			//use piano notes instead of songs as audio source
			randVertsForSphere	= 3,
			ballIsMade			= 4,
			showVertNorms		= 5,
			showZones			= 6,
			stimZoneMates		= 7,			
			fftLogLoaded		= 8,
			audioLoaded 		= 9,
			playMP3Vis			= 10,
			useForcesForBall	= 11, 		//if true use forces to stimulate ball, if false just displace from rest position by level 
			sendAudioToBall		= 12,
			
			showZoneBandRes		= 13,		//overlay fft band energy bars on screen for zones
			showAllBandRes		= 14,		//overlay fft band energy bars on screen for all bands
			
			useHumanTapBeats	= 15,		//use human tapping for each zone beat, otherwise use detected beats
			showTapBeats		= 16,		//show tap beats on side of screen
			stimWithTapBeats	= 17,		//stimulate ball with tap beats, otherwise stimulate with audio
			
			showFreqLbls		= 18,		//overlay frequency labels on display of energy bars
			showPianoNotes		= 19,		//display piano notes being played
			calcSingleFreq		= 20,		//analyze signal with single frequencies
			showEachOctave 		= 21; 	
	public static final int numPrivFlags = 22;
	
	//piano display
	public float whiteKeyWidth = 78, bkModY;				//how long, in pixels, is a white key, blk key is 2/3 as long
	//displayed piano
	public int gridX, gridY;									//pxls per grid box

	
	//offset to bottom of custom window menu 
	private float custMenuOffset;
	
//	//minim audio-related variables
//	public final int fftMinBandwidth = 20, fftBandsPerOctave = 24;
//	public final int songBufSize = 1024;
//	public float[] blankRes1 = new float[songBufSize];
//	public float[] blankRes2 = new float[songBufSize];
//	//per zone avg frequencies
//	public float[] //blankBands = new float[numZones], 
//			bandRes = new float[numZones], bandFreqs = new float[numZones], 
//			allBandsRes = new float[numZones], allBandFreqs = new float[numZones];
//	public boolean[] beatDetRes = new boolean[numZones], lastBeatDetRes = new boolean[numZones];
//	//threads for working on dft analysis
//	public List<myDFTNoteMapper> callDFTNoteMapper;
//	public List<Future<Boolean>> callDFTMapperFtrs;
//	
//	public myMP3SongHandler[] pianoClips;
	public String[] pianoNoteFilenames = new String[] {
			"piano-ff-029.wav","piano-ff-030.wav","piano-ff-031.wav","piano-ff-050.wav",
			"piano-ff-051.wav","piano-ff-052.wav","piano-ff-053.wav","piano-ff-054.wav"};
	public String[] pianoNoteList = new String[] {"ff-029","ff-030","ff-031","ff-050","ff-051","ff-052","ff-053","ff-054"};
//	
//	public myMP3SongHandler[] songs;
	public String[] songTitles = new String[]{"sati.mp3","PurpleHaze.mp3","UNATCO.mp3","karelia.mp3","choir.mp3"};
	public String[] songList = new String[]{"Sati","PurpleHaze","UNATCO","Karelia","Choir"};
//	
//	WindowFunction[] windowList = new WindowFunction[]{FFT.NONE, FFT.BARTLETT, FFT.BARTLETTHANN, FFT.BLACKMAN, FFT.COSINE, FFT.GAUSS, FFT.HAMMING, FFT.HANN, FFT.LANCZOS, FFT.TRIANGULAR};
	String[] windowNames = new String[]{"NONE","BARTLETT","BARTLETTHANN","BLACKMAN","COSINE","GAUSS","HAMMING","HANN","LANCZOS","TRIANGULAR"};
//	
//	//beat interaction
//	public myBeat[] tapBeats, audioBeats;	
	
	public DancingBallWin(DancingBalls _p, String _n, int _flagIdx, int[] fc, int[] sc, float[] rd, float[] rdClosed, String _winTxt, boolean _canDrawTraj) {
		super(_p, _n, _flagIdx, fc, sc, rd, rdClosed, _winTxt, _canDrawTraj);
		float stY = rectDim[1]+rectDim[3]-4*yOff,stYFlags = stY + 2*yOff;
		trajFillClrCnst = DancingBalls.gui_DarkCyan;		//override this in the ctor of the instancing window class
		trajStrkClrCnst = DancingBalls.gui_Cyan;
		super.initThisWin(_canDrawTraj, true, false);
	}
	
	public void updateGridXandY(boolean resize){
		gridX = (int)(rectDim[2] * gridXMult);
		gridY = (int)(rectDim[3] * gridYMult);
		bkModY = .3f * gridY;
		if(resize){
			dispPiano.updateDims(gridX, gridY, new float[]{0, topOffY, whiteKeyWidth, 52 * gridY}, rectDim);
		}
	}
	public static int calcGridWidth(float winWidth){return (int)(winWidth*gridXMult);}
	public static int calcGridHeight(float winHeight){return (int)(winHeight*gridYMult);}

	
	@Override
	//initialize all private-flag based UI buttons here - called by base class
	public void initAllPrivBtns(){
		truePrivFlagNames = new String[]{								//needs to be in order of privModFlgIdxs
				"Debugging","Mod DelT By FRate","Random Ball Verts","Showing Vert Norms","Showing Zones", 
				"Stim Zone and Mate", "Playing MP3","Use Piano Note files","Mass-Spring Ball", "Dancing", 
				"Stim Ball W/Beats","Showing Beats","Use Human Tap Beats", 
				"Showing Ctr Freq Vals","Showing Zone EQ", "Showing All Band Eq","Showing Piano","Showing Per Thd Note","Note Lvls w/Indiv F"	
		};
		falsePrivFlagNames = new String[]{			//needs to be in order of flags
				"Enable Debug","Fixed DelT","Uniform Ball Verts","Hiding Vert Norms", "Hiding Zones",
				"Stim Only Zones","Stopped MP3","Use Song Files","Kinematics Ball","Not Dancing", 
				"Stim Ball W/Audio","Hiding Beats","Use Detected Beats",  
				"Hiding Ctr Freq Vals", "Hiding Zone EQ", "Hiding All Band Eq", "Hiding Piano","Showing One Note", "Note Lvls w/FFT"
		};
		privModFlgIdxs = new int[]{
				debugAnimIDX, modDelT,randVertsForSphere,showVertNorms,showZones,
				stimZoneMates,playMP3Vis, usePianoNoteFiles, useForcesForBall, sendAudioToBall,  
				stimWithTapBeats, showTapBeats, useHumanTapBeats, 
				showFreqLbls, showZoneBandRes, showAllBandRes, showPianoNotes,showEachOctave, calcSingleFreq
		};
		numClickBools = privModFlgIdxs.length;	
		initPrivBtnRects(0,numClickBools);
	}//initAllPrivBtns
	
	@Override
	protected void initMe() {
		//build ball object
		//scale z val == 1 is sphere, <1 is ellipsoid
		ball = new myDancingBall(pa, this, "Ball for zone : " + name,new myVectorf(0,0,0),ballRadius, 1.0f);
		//piano to display on size of window
		updateGridXandY(false);
		dispPiano = new myPianoObj(pa, this,  gridX, gridY, new float[]{0, topOffY, whiteKeyWidth, 52 * gridY}, fillClr, rectDim);		//start with 52 white keys (full keyboard)

//		songs = new myMP3SongHandler[songTitles.length];
//		pianoClips = new myMP3SongHandler[pianoNoteFilenames.length];

		//called once
		initPrivFlags(numPrivFlags);
		//this window is runnable
		setFlags(isRunnable, true);
		//this window uses a customizable camera
		setFlags(useCustCam, true);
		custMenuOffset = uiClkCoords[3];	//495
		rebuildDancingBall();
		
		//audMgr needs to be built after dispPiano and flags values have been set	
		audMgr = new myAudioManager(pa, this);	
		
		//after construction, current flag state needs to be sent to audio mgr
		
		
		//init piano freqs
		//ConcurrentSkipListMap<Float, Integer> allFreqsUsed = 
		//dispPiano.initPianoFreqs();
		//initialize tap beat structures
		//initTapBeatStructs();

		//load all songs, add sample rate to 
		//loadSongsAndFFT();		
		//launch thread to precalculate all trig stuff
		
		//pa.th_exec.execute(new myTrigPrecalc(this, allFreqsUsed) );
		//build DFT threads and precalc local cos/sin values
		//initDFTAnalysisThrds(10);
		
		//initialize results for piano tracking

	}//initMe	

	
//	//initialize array of mybeat to hold results of tapped beat data
//	protected void initTapBeatStructs() {
//		//beat holding array
//		tapBeats = new myBeat[numZones];
//		audioBeats = new myBeat[numZones];
//		for(int i=0;i<numZones; ++i) {	tapBeats[i] = new myBeat(pa,i);	audioBeats[i] = new myBeat(pa,i);	}		
//	}//initTapBeatStructs
//	
//	//either returns current song or current piano clip
//	protected myMP3SongHandler getCurrentClip(int idx) {
//		if (getPrivFlags(usePianoNoteFiles)) {
//			return pianoClips[idx];
//		} else {
//			return songs[idx];
//		}		
//	}
//	
//	protected void setFFTVals() {
//		if (this.getPrivFlags(fftLogLoaded)){
//			for(int i=0;i<songTitles.length;++i){songs[i].setFFTVals(windowList[curWindowIDX], fftMinBandwidth, fftBandsPerOctave, numZones);}	
//			for(int i=0;i<pianoNoteFilenames.length;++i){	pianoClips[i].setFFTVals(windowList[curWindowIDX], fftMinBandwidth, fftBandsPerOctave, numZones);}				
//		}
//		
//	}//setFFTVals
//	
//	protected void loadSongsAndFFT() {
//		sampleRates = new ConcurrentSkipListMap<Float, Integer>();//hold only sample rates that we have seen
//		for(int i=0;i<songTitles.length;++i){	songs[i] = new myMP3SongHandler(pa.minim, songTitles[i], songList[i], songBufSize);	sampleRates.put(songs[i].playMe.sampleRate(), 1);}		
//		for(int i=0;i<pianoNoteFilenames.length;++i){	pianoClips[i] = new myMP3SongHandler(pa.minim, pianoNoteFilenames[i], pianoNoteList[i], songBufSize);	sampleRates.put(pianoClips[i].playMe.sampleRate(), 1);}		
//		setPrivFlags(audioLoaded,true);
//		setPrivFlags(fftLogLoaded, true);
//		setFFTVals();
//	}//loadSongList() 		
//	
	/**
	 * build dancing ball
	 */	
	private void rebuildDancingBall() {
		ball.buildVertsAndNorms(ballRadius, ballNumVerts, getPrivFlags(randVertsForSphere));		
	}//buildDancingBall	
	
	//set ball reset all verts in zones being displaced, before changing to a new zone
	private void resetBallDisplacement() {
		ball.resetVertLocs(zoneToShow, zoneMmbrToShow, true);
	}
	
	//set once ball is either being rebuilt or is finished being built
	public void setBallIsMade(boolean val) {	setPrivFlags(ballIsMade, val);}
	public boolean getBallIsMade() {	return getPrivFlags(ballIsMade);}
	public boolean getStimZoneMates() {	return getPrivFlags(stimZoneMates);}
	
	@Override
	//set flag values and execute special functionality for this sequencer
	public void setPrivFlags(int idx, boolean val){
		boolean curVal = getPrivFlags(idx);
		if(val == curVal) {return;}
		int flIDX = idx/32, mask = 1<<(idx%32);
		privFlags[flIDX] = (val ?  privFlags[flIDX] | mask : privFlags[flIDX] & ~mask);
		switch(idx){
			case debugAnimIDX 			: {
				break;}
			case randVertsForSphere		: {//rebuild sphere with selected vert type (either random or uniformly placed
				rebuildDancingBall();
				break;}
			case showVertNorms			: {break;}
			case ballIsMade				: {//changes when notification is received that ball's state has changed
				break;}
			case showZones				: {break;}
			case stimZoneMates			: {break;}
			//audio manager flags -> send to audio manager
			case playMP3Vis				: {
				if(val) {	audMgr.startAudio();}
				else {		audMgr.pauseAudio();}
				break;}
			case usePianoNoteFiles 		: {//change display to be list of piano note files
				setPrivFlags(playMP3Vis,false);//turn off playing
				if (val) {//use piano notes files
					guiObjs[gIDX_curSong].setNewMax(pianoNoteList.length-1);
					audMgr.songIDX %= pianoNoteList.length;
				} else {//use song files
					guiObjs[gIDX_curSong].setNewMax(songList.length-1);			
					audMgr.songIDX %= songList.length;
				}
				
				break;}
			case sendAudioToBall 		: {break;}
			case useForcesForBall		: {break;}
			case showZoneBandRes: {
				if(val) {setPrivFlags(showAllBandRes, false);}//either or allowed,not both
				break;}
			case showAllBandRes: {
				if(val) {setPrivFlags(showZoneBandRes, false);}
				break;}
			case calcSingleFreq : {
				if(!val) {setPrivFlags(showEachOctave, false);}//only show each octave results when calculating dft-based
				break;}
			case showEachOctave : {
				if(val) {setPrivFlags(calcSingleFreq, true);}//only show each octave results when calculating dft-based		
				break;}
		}		
	}//setPrivFlags	
	
	//initialize structure to hold modifiable menu regions
	@Override
	protected void setupGUIObjsAras(){	
		guiMinMaxModVals = new double [][]{
			{0,1.0f,.001f},					//timestep           		gIDX_TimeStep 	
			{200,10000,10},						//# of vertices
			{50,1000,10},						//ball at-rest radius
			{5, 100, 5},				//min neighborhood size fraction of number of verts			
			{0,numZones-1,1},				//zone to show if showing zones on sphere
			{0,10000,1},					//zone member to show if showing zones on sphere (% list size, so can be huge)
			{0.0, songTitles.length-1, 0.1},	//song/clip selected
			{0.0, windowNames.length-1, 1.0},	//window function selected
		};		//min max mod values for each modifiable UI comp	

		guiStVals = new double[]{
			uiVals[gIDX_TimeStep],	
			uiVals[gIDX_NumVerts],
			uiVals[gIDX_BallRad],
			uiVals[gIDX_minVertNBHD],
			uiVals[gIDX_zoneToShow],
			uiVals[gIDX_zoneMmbrToShow],
			uiVals[gIDX_curSong],
			uiVals[gIDX_winSel]
		};								//starting value
		
		guiObjNames = new String[]{
				"Time Step",
				"# of Vertices in Ball",
				"Rest Radius of Ball",
				"Min Ratio # Verts in NBHD",
				"Zone to show on Sphere",
				"Zone Member to Show",
				"MP3 Song",
				"FFT Window func"
		};								//name/label of component	
		
		//idx 0 is treat as int, idx 1 is obj has list vals, idx 2 is object gets sent to windows
		guiBoolVals = new boolean [][]{
			{false, false, true},	//timestep           		gIDX_TimeStep 	
			{true, false, true},
			{false, false, true},
			{true, false, true},
			{true, false, true},
			{true, false, true},
			{true, true, true},
			{true, true, true}
		};						//per-object  list of boolean flags
		
		//since horizontal row of UI comps, uiClkCoords[2] will be set in buildGUIObjs		
		guiObjs = new myGUIObj[numGUIObjs];			//list of modifiable gui objects
		if(numGUIObjs > 0){
			buildGUIObjs(guiObjNames,guiStVals,guiMinMaxModVals,guiBoolVals,new double[]{xOff,yOff});			//builds a horizontal list of UI comps
		}
//		setupGUI_XtraObjs();
	}//setupGUIObjsAras
	
//	//setup UI object for song slider
//	private void setupGUI_XtraObjs() {
//		double stClkY = uiClkCoords[3], sizeClkY = 3*yOff;
//		guiObjs[songTransIDX] = new myGUIBar(pa, this, songTransIDX, "MP3 Transport for ", 
//				new myVector(0, stClkY,0), new myVector(uiClkCoords[2], stClkY+sizeClkY,0),
//				new double[] {0.0, 1.0,0.1}, 0.0, new boolean[]{false, false, true}, new double[]{xOff,yOff});	
//		
//		//setup space for ui interaction with song bar
//		stClkY += sizeClkY;				
//		uiClkCoords[3] = stClkY;
//	}

	
	@Override
	protected void setUIWinVals(int UIidx) {
		float val = (float)guiObjs[UIidx].getVal();
		//int ival = (int)val;
		if(val != uiVals[UIidx]){//if value has changed...
			uiVals[UIidx] = val;
			switch(UIidx){		
			case gIDX_TimeStep 			:{
				if(val != deltaT){deltaT = val;}
				break;}
			case gIDX_NumVerts			:{
				ballNumVerts = (int)val;
				rebuildDancingBall();
				break;}
			case gIDX_BallRad		:{
				ballRadius = (int)val;
				rebuildDancingBall();
				break;}
			case gIDX_minVertNBHD		:{
				minVInNBD = (int)val;
				rebuildDancingBall();
				break;}
			case gIDX_zoneToShow 	:{
				resetBallDisplacement();
				zoneToShow = (int)val;
				break;}
			case gIDX_zoneMmbrToShow :{
				resetBallDisplacement();
				zoneMmbrToShow = ((int)val) % ball.zonePoints[zoneToShow].length;
				//reset UI display value to be zoneMmbrToShow
				guiObjs[UIidx].setVal(zoneMmbrToShow);
				break;}
			case gIDX_curSong 	: {
				audMgr.changeCurrentSong((int)val);
				ball.resetVertLocs();
				break;}
			case gIDX_winSel		: {
				audMgr.changeCurrentWindowfunc((int)val);	
				break;}
			default : {break;}
			}
		}
	}
	
//	//stop all clips from playing
//	protected void stopAllPlaying() {
//		for(int i=0;i<songTitles.length;++i){	songs[i].pause();	}		
//		for(int i=0;i<pianoNoteFilenames.length;++i){	pianoClips[i].pause();	}				
//	}
//	//rewind current song
//	public void rewindSong() {
//		if(getPrivFlags(playMP3Vis)){
//			this.getCurrentClip(songIDX).rewind();
//		}		
//	}
	//if any ui values have a string behind them for display
	@Override
	protected String getUIListValStr(int UIidx, int validx) {
		switch(UIidx){
			case gIDX_curSong : {return 
					getPrivFlags(this.usePianoNoteFiles) ? 
							pianoNoteList[(validx % pianoNoteList.length)] :	
							songList[(validx % songList.length)]; }
			case gIDX_winSel  : {return windowNames[(validx % windowNames.length)]; }
			default : {break;}
		}
		return "";
	}
	
	public float getTimeStep(){
		return uiVals[gIDX_TimeStep] * timeStepMult;
	}
	
	//TODO handle transport display
//	private void setSongTransInfo() {
//		guiObjs[songTransIDX].setName("MP3 Transport for " + songs[songIDX].dispName);
//		guiObjs[songTransIDX].setVal(songs[songIDX].getPlayPosRatio());		
//	}

	
//	public void changeCurrentSong(int newSongIDX){
//		this.getCurrentClip(songIDX).pause();
//		ball.resetVertLocs();
//		songIDX = newSongIDX;
//		if(getPrivFlags(playMP3Vis)){this.getCurrentClip(songIDX).play();}
//	}//changeCurrentSong
//	public void changeCurrentWindowfunc(int newWinFuncIDX) {
//		curWindowIDX = newWinFuncIDX;
//		if(getPrivFlags(fftLogLoaded)) {setFFTVals();}
//	}//changeCurrentWindowfunc
//
//
//	public void startAudio(){
//		if(!getPrivFlags(audioLoaded)){loadSongsAndFFT();}//load songs if not loaded already
//		//pa.outStr2Scr("Song in buffer : " + songTitles[songIDX] + " size: " +  songs[songIDX].bufferSize() + " Sample rate : "+ songs[songIDX].sampleRate());
//		this.getCurrentClip(songIDX).play();
//		//send frequencies from fft 
////		if((!privFlags[oceanMadeIDX]) || (null == ball) || (!fftOcean.cudaFlags[fftOcean.doneInit])){return;}
//		//ball.setFreqVals(blankRes1, blankRes2,blankBands);			
//	}
//
//	public void pauseAudio(){
//		if(getPrivFlags(audioLoaded)){
//			stopAllPlaying();
//			//this.getCurrentClip(songIDX).pause();
//			//songs[songIDX].pause();
//		}		
//	}
//	
//	
//	public void initDFTAnalysisThrds(int numThreads) {
//		//threads 0-3 are bass range
//		//4-6 are mid range
//		//7-9 are treble range.  perhaps use these to calculate zone behavior?
//		callDFTNoteMapper = new ArrayList<myDFTNoteMapper>();
//		callDFTMapperFtrs = new ArrayList<Future<Boolean>>();
//		int numPerThread = 1+(dispPiano.pianoFreqsHarmonics.length-1)/numThreads;
//		int stIdx = 0, endIdx = numPerThread-1;
//		for (int i =0;i<numThreads;++i) {		
//			callDFTNoteMapper.add(new myDFTNoteMapper(this,stIdx,endIdx));	
//			stIdx = endIdx + 1;
//			endIdx = stIdx + numPerThread-1;
//			if(endIdx > dispPiano.pianoFreqsHarmonics.length - 1) {endIdx = dispPiano.pianoFreqsHarmonics.length-1;}
//		}
//		levelsPerPKeySingleCalc = new ConcurrentSkipListMap<Float, Integer>(new Comparator<Float>() {
//            @Override
//            public int compare(Float o1, Float o2) {   return o2.compareTo(o1);}
//        });
//		bassLvlsPerKey = new ConcurrentSkipListMap<Float, Integer>(new Comparator<Float>() {
//            @Override
//            public int compare(Float o1, Float o2) {   return o2.compareTo(o1);}
//        });
//		midLvlsPerKey = new ConcurrentSkipListMap<Float, Integer>(new Comparator<Float>() {
//            @Override
//            public int compare(Float o1, Float o2) {   return o2.compareTo(o1);}
//        });
//		trblLvlsPerKey = new ConcurrentSkipListMap<Float, Integer>(new Comparator<Float>() {
//            @Override
//            public int compare(Float o1, Float o2) {   return o2.compareTo(o1);}
//        });
//	}
//	
//	//set every time this is run before execution
//	private void setPerRunRes(float sampleRate, float[] _buffer) {
//		bassLvlsPerKey.clear();
//		midLvlsPerKey.clear();
//		trblLvlsPerKey.clear();
//		
//		//threads 0-3 are bass range
//		//4-6 are mid range
//		//7-9 are treble range.  perhaps use these to calculate zone behavior?
//		levelsPerPKeySingleCalc.clear();
////		ConcurrentSkipListMap<Float, Float[]> lclCosTbl = cosTbl.get(sampleRate);
////		ConcurrentSkipListMap<Float, Float[]> lclSinTbl = sinTbl.get(sampleRate);
//		
////		for(int i=0;i<4;++i) {callDFTNoteMapper.get(i).setPerRunValues(sampleRate, _buffer, lclCosTbl, lclSinTbl,bassLvlsPerKey);}
////		for(int i=4;i<7;++i) {callDFTNoteMapper.get(i).setPerRunValues(sampleRate, _buffer, lclCosTbl, lclSinTbl,midLvlsPerKey);}
////		for(int i=7;i<10;++i) {callDFTNoteMapper.get(i).setPerRunValues(sampleRate, _buffer, lclCosTbl, lclSinTbl,trblLvlsPerKey);}
//		
//		for(int i=0;i<4;++i) {callDFTNoteMapper.get(i).setPerRunValues(sampleRate, _buffer,levelsPerPKeySingleCalc, bassLvlsPerKey);}
//		for(int i=4;i<7;++i) {callDFTNoteMapper.get(i).setPerRunValues(sampleRate, _buffer, levelsPerPKeySingleCalc,midLvlsPerKey);}
//		for(int i=7;i<10;++i) {callDFTNoteMapper.get(i).setPerRunValues(sampleRate, _buffer, levelsPerPKeySingleCalc,trblLvlsPerKey);}
//		
//		//for (myDFTNoteMapper mapper : callDFTNoteMapper) {mapper.setPerRunValues(sampleRate, _buffer, lclCosTbl, lclSinTbl);}
//	}//setPerRunRes
	
	
//	//set process audio for each frame
//	public void processAudioData() {
//		myMP3SongHandler song = this.getCurrentClip(songIDX);
//		//songs[songIDX].fftFwdOnAudio();
//		song.fftFwdOnAudio();
//		float[][] res ;
////		res = songs[songIDX].fftSpectrumFromAudio(); // real and imaginary components of frequency from sample window
////		res = song.fftSpectrumFromAudio(); // real and imaginary components of frequency from sample window
//		//all bands
//		//only perform if showing zone bands or ball is receiving audio
//		if(getPrivFlags(stimWithTapBeats) || getPrivFlags(showZoneBandRes) || getPrivFlags(sendAudioToBall) || (getPrivFlags(showTapBeats) && !getPrivFlags(useHumanTapBeats) )) {
//			res = song.fftFwdNumBandsFromAudio();
//			bandRes = res[0];
//			bandFreqs = res[1];
//			ball.setFreqVals(bandRes);
//		}
//		//only perform if showing all bands eq
//		if(getPrivFlags(showAllBandRes)) {
//			//res = songs[songIDX].fftFwdBandsFromAudio();
//			res = song.fftFwdBandsFromAudio();
//			allBandsRes = res[0];
//			allBandFreqs = res[1];		
//		}		
//		//analyze frequencies of center notes of piano manually
//		if(getPrivFlags(calcSingleFreq)) {			
//			setPerRunRes(song.playMe.sampleRate(),song.playMe.mix.toArray());	//send updates to dftAnalyzer		
//			try {callDFTMapperFtrs = pa.th_exec.invokeAll(callDFTNoteMapper);for(Future<Boolean> f: callDFTMapperFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }			
//		}		
//		if(getPrivFlags(showPianoNotes)) {levelsPerPianoKeyFund = song.fftFwdFreqLevelsInHarmonicBands(dispPiano.pianoMinFreqsHarmonics);}
//		//if we're showing beat detected and we're not using human tapped beats
//		if((getPrivFlags(showTapBeats) || getPrivFlags(stimWithTapBeats)) && ! getPrivFlags(useHumanTapBeats)) {
//			for (int i =0;i<lastBeatDetRes.length;++i) {lastBeatDetRes[i] = beatDetRes[i];}
//			beatDetRes = songs[songIDX].beatDetectZones();
//			for (int i =0;i<beatDetRes.length;++i) {if(beatDetRes[i]) {	audioBeats[i].addTap(pa.millis()); }		}//not properly measuring beat frequency - need to filter beats
//			
//			//pa.outStr2Scr("zone : 0 beat : " + beatDetRes[0]+" last beat det : " + lastBeatDetRes[0] );
//		} else {
//			lastBeatDetRes = new boolean[numZones];
//			beatDetRes = new boolean[numZones];
//		}
//		if(song.getPlayPosRatio() > .99) {//shut off songs if done
//			setPrivFlags(playMP3Vis, false);			
//		}
//	}//processAudioData


	//save the timing of a tapped beat of a certain type - map to zones for now
	//when this is called, key was pressed to signify the location of a beat of type key.
	//this will happen multiple times, and the average of the taps will represent the timing of the beat for type key
	public void saveTapBeat(int key) {
		audMgr.saveTapBeat(key);
//		if (getPrivFlags(useHumanTapBeats)) {tapBeats[key].addTap(pa.millis());} 
//		else {//if not enabled (not shown) then reset tapBeats struct
//			initTapBeatStructs();
//		}		
	}//saveTapBeat
	@Override
	public void initDrwnTrajIndiv(){}
	
	public void setLights(){
		pa.ambientLight(102, 102, 102);
		pa.lightSpecular(204, 204, 204);
		pa.directionalLight(180, 180, 180, 0, 1, -1);	
	}	
	//overrides function in base class mseClkDisp
	@Override
	public void drawTraj3D(float animTimeMod,myPoint trans){}//drawTraj3D	
	//set camera to either be global or from pov of one of the boids
	@Override
	protected void setCameraIndiv(float[] camVals, float rx, float ry, float dz){
		pa.camera(camVals[0],camVals[1],camVals[2],camVals[3],camVals[4],camVals[5],camVals[6],camVals[7],camVals[8]);      
		// puts origin of all drawn objects at screen center and moves forward/away by dz
		pa.translate(camVals[0],camVals[1],(float)dz); 
	    pa.setCamOrient();	
	}//setCameraIndiv
	
//	//draw bar representing level at a certain band
//	private void drawFreqBands(float[] bandRes, float[] bandFreqs, float height, int clr, boolean drawBeats, boolean showFreqs) {
//		pa.pushMatrix();pa.pushStyle();
//		float transY =-2*height;
//		float drawBeatsOffset = drawBeats ? 20 : 0, showFreqsOffset = showFreqs ? (height == 1) ? 180 : 40 : 0;
//		pa.translate( 10 + drawBeatsOffset,  rectDim[3]+transY);
//		float width = (rectDim[2]-(40 + drawBeatsOffset + showFreqsOffset)),
//				//scale value of how wide to draw the actual data
//				wdLogMult = width/6.0f,
//				wdMult = width/80.0f;
//		if(showFreqs) {
//			float txtHt = .4f*height + 5.0f;
//			pa.translate(showFreqsOffset,0,0);
//			pa.pushMatrix();pa.pushStyle();
//			pa.setStroke(strkClr);
//			pa.textSize(txtHt);
//			for (int i=0;i<bandRes.length;++i) {
//				pa.pushMatrix();pa.pushStyle();
//				//alternate display of freqs on either side of line
//				if(height == 1) {pa.translate(-30 *(1 + (i%6)),0,0);} else {pa.translate(-showFreqsOffset,0,0);}// tiny bar do staggered translate
//				pa.text(String.format("%5.1f", bandFreqs[i]), -3.0f, txtHt);
//				//draw ctr freq name				
//				pa.popStyle();pa.popMatrix();		
//				pa.translate(0,transY);
//			}
//			pa.popStyle();pa.popMatrix();		
//		}
//		for (int i=0;i<bandRes.length;++i) {
//			//draw freq bar
//			if(height > 1) {//not tiny bar
//				pa.noFill();
//				pa.setStroke(strkClr);
//				pa.rect(0,0,width, height);			
//				pa.setColorValFill(clr);
//			} else {//height is 1==tiny bar
//				pa.setColorValFill(clr);
//				if(i % 100 == 0) {pa.setColorValFill(pa.gui_White);}
//			}
//			pa.noStroke();
//			//pa.rect(0,0,wdMult * bandRes[i], height);		
//			pa.rect(0,0,wdLogMult * (float)Math.log1p(bandRes[i]), height);		
//			pa.translate(0,transY);
//		}		
//		pa.popStyle();pa.popMatrix();		
//	}//drawFreqBand
//	
//	//draw representations of beats on screen
//	private void drawBeats(myBeat[] beats, float modAmtMillis, float height) {
//		float rad = height/2.0f;
//		pa.pushMatrix();pa.pushStyle();
//		float transY =-2*height;
//		pa.translate( 10 + rad,  rectDim[3]+transY + rad);
//		for (int i=0;i<beats.length;++i) {			
//			beats[i].drawBeat(modAmtMillis, rad);
//			pa.translate(0,transY);
//		}
//		pa.popStyle();pa.popMatrix();	
//	}//drawTapBeats()
//	
//	//display beats detected in music
//	private void drawDetectedBeats(boolean[] beatState, boolean[] lastBeatState, float modAmtMillis, float height) {
//		float rad = height/2.0f;
//		pa.pushMatrix();pa.pushStyle();
//		pa.noStroke();
//		float transY =-2*height;
//		pa.translate(10 + rad,  rectDim[3]+transY + rad);
//		for (int i=0;i<beatState.length;++i) {		
//			if (beatState[i]){	pa.fill(0,255,0,255);} 
//			else if (lastBeatState[i]) {pa.fill(255,0,0,255);}
//			else {				pa.fill(150,150,150,150);	}//show beat on and determine if it should be turned off			
//			pa.sphere(rad);	
//			pa.translate(0,transY);
//		}
//		pa.popStyle();pa.popMatrix();	
//	}//drawDetectedBeats
	
	@Override
	//draw 2d constructs over 3d area on screen
	protected void drawOnScreenStuff(float modAmtMillis) {
		pa.pushMatrix();pa.pushStyle();
		//move to side of menu
		pa.translate(rectDim[0],0,0);
		audMgr.drawScreenData(modAmtMillis);
//		pa.hint(PConstants.DISABLE_DEPTH_TEST);
//		float bandResHeight = 10.0f;
//		boolean showBeats = getPrivFlags(showTapBeats);
//		if(getPrivFlags(showPianoNotes)) {
//			dispPiano.drawMe();	
//			if(getPrivFlags(calcSingleFreq) ) {
//				if(!getPrivFlags(showAllBandRes)) {dispPiano.drawPianoBandRes( levelsPerPKeySingleCalc);}
//				if(getPrivFlags(showEachOctave)) {
//					float bandThresh = 5.0f;//TODO set this to something to shut down multi-thread results that are very low
//					//threads 0-3 are bass range
//					//4-6 are mid range
//					//7-9 are treble range.  perhaps use these to calculate zone behavior?
//					dispPiano.drawPlayedNote(bassLvlsPerKey, bandThresh, 3, 2);
//					dispPiano.drawPlayedNote(midLvlsPerKey, bandThresh, 4, 2);
//					dispPiano.drawPlayedNote(trblLvlsPerKey, bandThresh, 5, 2);
//				} else {					dispPiano.drawPlayedNote(levelsPerPianoKeyFund, 0, pa.gui_Green, 3);	}
//			} else {
//				if(!getPrivFlags(showAllBandRes)) {dispPiano.drawPianoBandRes(levelsPerPianoKeyFund);}
//				dispPiano.drawPlayedNote(levelsPerPianoKeyFund, 0 ,pa.gui_Green, 3);
//			}
//			if(!getPrivFlags(showAllBandRes)) {dispPiano.drawPianoBandRes(getPrivFlags(calcSingleFreq) ? levelsPerPKeySingleCalc : levelsPerPianoKeyFund);}
//		}		
//		if (getPrivFlags(showAllBandRes)) {//if showing all bands, displace by piano keys' width
//			if(getPrivFlags(showPianoNotes)) {	//move over for piano				
//				pa.pushMatrix();pa.pushStyle();
//				pa.translate(whiteKeyWidth,0,0);				
//			}
//			drawFreqBands(allBandsRes, allBandFreqs, 1.0f, pa.gui_TransRed, showBeats, getPrivFlags(showFreqLbls));
//		}
//		else if(getPrivFlags(showZoneBandRes)) {drawFreqBands(bandRes, bandFreqs, bandResHeight, pa.gui_Blue, showBeats, getPrivFlags(showFreqLbls));}
//		if(showBeats) {
//			if(getPrivFlags(useHumanTapBeats)) {	drawBeats(tapBeats,modAmtMillis, bandResHeight);}//using human-entered tap beats 
//			else {								drawDetectedBeats(beatDetRes, lastBeatDetRes, modAmtMillis, bandResHeight);}//using music-generated beats
//		}
//		if(getPrivFlags(showAllBandRes) && getPrivFlags(showPianoNotes)) {	pa.popStyle();pa.popMatrix();	}		//undo piano translation
//		
//		pa.hint(PConstants.ENABLE_DEPTH_TEST);		
		pa.popStyle();pa.popMatrix();				
	}//drawOnScreenStuff
	
	@Override
	protected void drawMe(float animTimeMod) {
//		curMseLookVec = pa.c.getMse2DtoMse3DinWorld(pa.sceneCtrVals[pa.sceneIDX]);			//need to be here
//		curMseLoc3D = pa.c.getMseLoc(pa.sceneCtrVals[pa.sceneIDX]);
		//int stVal = pa.millis(); //takes 0 millis to process audio data
		audMgr.processAudioData();//get next set of audio data to process
		//pa.outStr2Scr("took : " + (pa.millis() - stVal) + " millis to processAudioData()");
		//int stVal = pa.millis();//takes around 90 millis to draw ball
		ball.drawMe(animTimeMod,zoneToShow,getPrivFlags(showZones), getPrivFlags(stimZoneMates), getPrivFlags(showVertNorms));
		//pa.outStr2Scr("took : " + (pa.millis() - stVal) + " millis to ball.drawMe()");
	}//drawMe	
	
	
	@Override
	public void drawCustMenuObjs(){
		pa.pushMatrix();				pa.pushStyle();		
		//all sub menu drawing within push mat call
		pa.translate(5,custMenuOffset+yOff);
		//draw any custom menu stuff here
		pa.popStyle();					pa.popMatrix();		
	}

	private float timePassed = 0;
	
	/**
	 * send to ball(s) all values necessary for step of simulation.  
	 * these include, if mass-spring : 
	 * 		ks vals dependent on beat freqs
	 * 		
	 * if kinematic : 
	 * 
	 * and for both types : 
	 * 		ui-selected zone(s) and zone members for each zone to display
	 * 		beat detection results and last beat detection results (previous frame)
	 */
	private void setBallSimulateVals() {
		//need to pre-calculate per-zone beat frequencies that we want to use to excite zones
		float[] zoneFreqs = new float[numZones];
		
		//set all required values for ball stimulation
		ball.setBallSimVals(zoneToShow,zoneMmbrToShow,audMgr.beatDetRes,audMgr.lastBeatDetRes);
		
		//set ball zone spring constants
		ball.setZoneKs();
	}

	
	@Override
	//modAmtMillis is time passed per frame in milliseconds
	protected void simMe(float modAmtMillis) {//run simulation
		if(!getPrivFlags(ballIsMade)) {return;}
		//int stVal = pa.millis();//takes around 5 millis to sim ball
		if(getPrivFlags(sendAudioToBall)) {
			//ball.stimulateBall(getPrivFlags(useForces), modAmtMillis);
			setBallSimulateVals();
			ball.stimMe( modAmtMillis,  getPrivFlags(stimWithTapBeats),  getPrivFlags(useForcesForBall)); 
//			
//			if(getPrivFlags(stimWithTapBeats)) {//stimulate ball with finger taps/ detected beats - intended to be debugging mechanism
//				if(getPrivFlags(useForcesForBall)){
//					//use force deformations
//					ball.stimBallTapsMassSprng(zoneToShow,zoneMmbrToShow,beatDetRes[zoneToShow],lastBeatDetRes[zoneToShow]);
//				} else {
//					//use kinematic deformations
//					ball.stimBallTapsKine(zoneToShow,zoneMmbrToShow,beatDetRes[zoneToShow],lastBeatDetRes[zoneToShow]);
//				}				
//			} else {//stimulate with pure audio
//				if(getPrivFlags(useForcesForBall)){
//					//use force deformations
//					ball.stimulateBallMassSpring(beatDetRes, lastBeatDetRes);
//				} else {
//					//use kinematic deformations
//					ball.resetVertLocs();		//reset ball shape every frame
//					ball.stimulateBallKine();
//				}
//			}
		}
		//pa.outStr2Scr("took : " + (pa.millis() - stVal) + " millis to ball simulate");

		//		timePassed += modAmtMillis;
//		float stimVal = (zoneToShow+1)*50.0f*(float)Math.sin(5.0f*timePassed);		
//		ball.stimulateZone(stimVal, zoneToShow, zoneMmbrToShow, getPrivFlags(stimZoneMates));
	}
	
	@Override
	protected void stopMe() {
		System.out.println("Stop");
		resetBallDisplacement();
	}		
	//debug function
	public void dbgFunc0(){		}	
	public void dbgFunc1(){		}	
	public void dbgFunc2(){		}	
	public void dbgFunc3(){		}	
	public void dbgFunc4(){		}	
	@Override
	public void clickDebug(int btnNum){
		pa.outStr2Scr("click debug in "+name+" : btn : " + btnNum);
		switch(btnNum){
			case 0 : {	dbgFunc0();	break;}
			case 1 : {	dbgFunc1();	break;}
			case 2 : {	dbgFunc2();	break;}
			case 3 : {	dbgFunc3();	break;}
			default : {break;}
		}		
	}
	
	@Override
	public void hndlFileLoadIndiv(String[] vals, int[] stIdx) {
		
	}

	@Override
	public List<String> hndlFileSaveIndiv() {
		List<String> res = new ArrayList<String>();

		return res;
	}
	@Override
	protected void processTrajIndiv(myDrawnSmplTraj drawnNoteTraj){	}
	@Override
	protected myPoint getMouseLoc3D(int mouseX, int mouseY){return pa.P(mouseX,mouseY,0);}
	@Override
	protected boolean hndlMouseMoveIndiv(int mouseX, int mouseY, myPoint mseClckInWorld){
		return false;
	}
	//alt key pressed handles trajectory
	//cntl key pressed handles unfocus of spherey
	@Override
	protected boolean hndlMouseClickIndiv(int mouseX, int mouseY, myPoint mseClckInWorld, int mseBtn) {
		boolean res = checkUIButtons(mouseX, mouseY);
		return res;
	}//hndlMouseClickIndiv

	@Override
	protected boolean hndlMouseDragIndiv(int mouseX, int mouseY, int pmouseX, int pmouseY, myPoint mouseClickIn3D, myVector mseDragInWorld, int mseBtn) {
		boolean res = false;
		return res;
	}
	
	@Override
	protected void snapMouseLocs(int oldMouseX, int oldMouseY, int[] newMouseLoc) {}	
	@Override
	protected void hndlMouseRelIndiv() {}
	@Override
	protected void endShiftKeyI() {}
	@Override
	protected void endAltKeyI() {}
	@Override
	protected void endCntlKeyI() {}
	@Override
	protected void addSScrToWinIndiv(int newWinKey){}
	@Override
	protected void addTrajToScrIndiv(int subScrKey, String newTrajKey){}
	@Override
	protected void delSScrToWinIndiv(int idx) {}	
	@Override
	protected void delTrajToScrIndiv(int subScrKey, String newTrajKey) {}
	//resize drawn all trajectories
	@Override
	protected void resizeMe(float scale) {
		updateGridXandY(true);		
	}
	@Override
	protected void closeMe() {}
	@Override
	protected void showMe() {}
}//DancingBallWin

