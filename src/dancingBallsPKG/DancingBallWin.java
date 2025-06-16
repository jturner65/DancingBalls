package dancingBallsPKG;

//
import java.util.*;


public class DancingBallWin extends Base_DispWindow {
	//set # zones here
	public static final int numZones = 6;	
	//Dancing Ball object
	public myDancer ball;
	//piano visualization object
	public myPianoObj dispPiano;	
	//audio manager - move all audio processing to this object
	public myAudioManager audMgr;	
	//song types
	public static final int mp3Song = 0, midiSong = 1;
	///////////
	//ui vals
	//sim timestep from ui
	public float deltaT = .01f;
	//# of verts for dancing ball
	public int ballNumVerts = 10000;
	//default radius of ball - for snowman scaled for each ball
	public float ballRadius = 200;
	//minimum fraction of vert count to belong in a neighborhood - if the derived # of verts for a particular zone is less than this, it will be forced to be this many
	public int minVInNBD = 10;
	//zone to display when displaying zones on sphere
	public int zoneToShow = 0;
	//zone member to show
	public int zoneMmbrToShow = 0;
	//idxs - need one per object
	public final static int
		gIDX_TimeStep 		= 0,
		gIDX_NumVerts		= 1,
		gIDX_BallRad		= 2,
		gIDX_minVertNBHD	= 3,
		gIDX_zoneToShow		= 4,
		gIDX_zoneMmbrToShow = 5,
		gIDX_curSongDir		= 6,		//either midi or "mp3" (catch all for audio)
		gIDX_curSongBank	= 7,
		gIDX_curSong 		= 8,
		gIDX_numNotesByLvl  = 9,		//top # of notes to show per lvl-mapping result 
		gIDX_audThresh		= 10,		//fraction of max level seen in entire sample set to display as key hits (0 means display all) - only used for multi-thread res
		gIDX_typeDFTToShow 	= 11,		//whether to show results for 0:global max, 1:freq zone max or 2:per-thread max note levels
		gIDX_noiseGate		= 12,		//absolute level below which input is ignored for interval calculation/piano roll display
		gIDX_DFTCalcType	= 13,		//list of possible calculation types
		gIDX_winSel 		= 14,
		gIDX_midiVolDispBin = 15;
	//initial values - need one per object
	protected float[] uiVals = new float[]{
			deltaT,
			ballNumVerts,
			ballRadius,
			minVInNBD,
			zoneToShow,
			zoneMmbrToShow,
			myAudioManager.songType,						//initial song type in audMgr
			myAudioManager.songBank,						//initial song bank in audMgr
			myAudioManager.songIDX,							//initial songIDX, in audMgr	
			myAudioManager.numNotesToShow,					//loudest # of notes to show per lvl-mapping result (min 1)
			myAudioManager.audThreshold*100,					//threshold fraction of max level seen, below which notes are not shown on keyboard
			myAudioManager.dftThdResToShow,					//default dft to show -> global
			myAudioManager.noiseGateLvl,					//absolute level below which input is ignored
			myAudioManager.calcFuncToUse,					//which dft calculation mechanism to use
			myAudioManager.curWindowIDX,					//	curWindowIDX in audMgr	
			myAudioManager.curBinSize						//current bin size for audio level display
	};			//values of 8 ui-controlled quantities
	public final int numGUIObjs = uiVals.length;											//# of gui objects for ui	
	
	/////////
	//ui button names -empty will do nothing
	public String[] menuDbgBtnNames = new String[]{"Ball Debug", "Check Memory", "Dbg 3", "Dbg 4"};//must have literals for every button or ignored
	public String[] menuFuncBtnNames = new String[]{"Init AudIO","Load Midi", "Proc/Save Midi", "Cust Func 3", "Cust Func 4"};//must have literals for every button or ignored
	
	
	public String[] dftResTypeToShow = new String[]{"Global","Per Zone","Per Thread"};
	//0:per sample, all harms, 1 : per sample, fund only, 2:all samples, fund only
	public String[] dftCalcTypeToUse = new String[]{"Per Smpl, All Harms","Per Smpl, Fund Only", "All Samples, Fund Only"};

	public float timeStepMult = 1.0f;													//multiplier to modify timestep to make up for lag
	
	//private child-class flags - window specific
	public static final int 
			debugAnimIDX 		= 0,						//debug
			modDelT				= 1,			//whether to modify delT based on frame rate or keep it fixed (to fight lag)
			randVertsForSphere	= 2,
			ballIsMade			= 3,
			showVertNorms		= 4,
			showZones			= 5,
			stimZoneMates		= 6,			
			playMP3Vis			= 7,
			useForcesForBall	= 8, 		//if true use forces to stimulate ball, if false just displace from rest position by level 
			sendAudioToBall		= 9,
			
			showZoneBandRes		= 10,		//overlay fft band energy bars on screen for zones
			showAllBandRes		= 11,		//overlay fft band energy bars on screen for all bands
			
			useHumanTapBeats	= 12,		//use human tapping for each zone beat, otherwise use detected beats
			showTapBeats		= 13,		//show tap beats on side of screen
			stimWithTapBeats	= 14,		//stimulate ball with tap beats, otherwise stimulate with audio
			
			showFreqLbls		= 15,		//overlay frequency labels on display of energy bars
			showPianoKbd		= 16,		//display piano notes being played
			showPianoNoteNames  = 17,		//whether or not to show piano note names
			showMelodyTrail		= 18,		//display "piano roll" trail of melody, otherwise show levels of signal for each piano key
			showIntrvls			= 19,		//display graphical representation of intervals
			showVolLvls			= 20,		//display graphical rep of volume level samples
			
			calcSingleFreq		= 21,		//analyze signal with single frequencies
			useSumLvl			= 22,		//use sum of each key's audio levels over the past n samples
			usePianoTune		= 23;		//use piano tuning or equal-tempered tuning

	public static final int numPrivFlags = 24;
	
	//display names of fft windows
	public String[] fftWinNames = new String[]{"NONE","BARTLETT","BARTLETTHANN","BLACKMAN","COSINE","GAUSS","HAMMING","HANN","LANCZOS","TRIANGULAR"};
	
	public DancingBallWin(DancingBalls _p, String _n, int _flagIdx, int[] fc, int[] sc, float[] rd, float[] rdClosed, String _winTxt, boolean _canDrawTraj) {
		super(_p, _n, _flagIdx, fc, sc, rd, rdClosed, _winTxt, _canDrawTraj);
		float stY = rectDim[1]+rectDim[3]-4*yOff,stYFlags = stY + 2*yOff;
		trajFillClrCnst = DancingBalls.gui_DarkCyan;		//override this in the ctor of the instancing window class
		trajStrkClrCnst = DancingBalls.gui_Cyan;
		super.initThisWin(_canDrawTraj, true, false);
	}//DancingBallWin
	
	@Override
	//initialize all private-flag based UI buttons here - called by base class
	public void initAllUIButtons(){
		truePrivFlagNames = new String[]{								//needs to be in order of privModFlgIdxs
				"Debugging","Mod DelT By FRate","Random Ball Verts","Showing Vert Norms","Showing Zones", 
				"Stim Zone and Mate","Mass-Spring Ball", "Dancing", 
				"Stim Ball W/Beats","Use Human Tap Beats", "Showing Beats",
				"Showing Ctr Freq Vals","Showing Zone EQ", "Showing All Band Eq","Showing Piano","Showing P-Key Names","Showing Melody Trail","Showing Intevals", "Showing Vol Lvls",//"Showing Per-Thd Notes",
				"Lvls via Indiv Freq","Use past N lvls sum","Use Piano Tuning", "Playing MP3"
		};
		falsePrivFlagNames = new String[]{			//needs to be in order of flags
				"Enable Debug","Fixed DelT","Uniform Ball Verts","Hiding Vert Norms", "Hiding Zones",
				"Stim Only Zones","Kinematics Ball","Not Dancing", 
				"Stim Ball W/Audio","Use Detected Beats","Hiding Beats",  
				"Hiding Ctr Freq Vals", "Hiding Zone EQ", "Hiding All Band Eq", "Hiding Piano", "Hiding P-Key Names","Showing Key lvls","Hiding Intervals", "Hiding Vol Lvls",//"Showing Glbl Max Note", 
				"Lvls via FFT","Use current lvl","Use Eq Tmpred Tuning","Stopped MP3"
		};
		privModFlgIdxs = new int[]{
				debugAnimIDX, modDelT,randVertsForSphere,showVertNorms,showZones,
				stimZoneMates, useForcesForBall, sendAudioToBall,  
				stimWithTapBeats,  useHumanTapBeats, showTapBeats,
				showFreqLbls, showZoneBandRes, showAllBandRes, showPianoKbd, showPianoNoteNames, showMelodyTrail, showIntrvls,showVolLvls, //showEachOctave, 
				calcSingleFreq,useSumLvl,usePianoTune,playMP3Vis
		};
		numClickBools = privModFlgIdxs.length;	
		initPrivBtnRects(0,numClickBools);
	}//initAllPrivBtns
	//set labels of boolean buttons 
	private void setLabel(int idx, String tLbl, String fLbl) {truePrivFlagNames[idx] = tLbl;falsePrivFlagNames[idx] = fLbl;}//	
	//update button text based on what type of song is being played (midi or mp3)
	public void updateButtons(int songType) {
		//pa.outStr2Scr("updateButtons start");
		switch(songType) {
		case mp3Song : {			
			setLabel(getFlagAraIdxOfBool(playMP3Vis), "Playing MP3", "Stopped MP3");			
			setLabel(getFlagAraIdxOfBool(calcSingleFreq), "Lvls via Indiv Freq", "Lvls via FFT");
			guiObjs_Numeric[gIDX_curSong].setDispText("MP3 Clip");
			break;}
		case midiSong :{
			setLabel(getFlagAraIdxOfBool(playMP3Vis), "Playing Midi", "Stopped Midi");
			setLabel(getFlagAraIdxOfBool(calcSingleFreq), "DFT levels", "Actual Midi lvls");
			guiObjs_Numeric[gIDX_curSong].setDispText("Midi Clip");
			break;}
		default : {//default to mp3 song
			setLabel(getFlagAraIdxOfBool(playMP3Vis), "Playing MP3", "Stopped MP3");			
			setLabel(getFlagAraIdxOfBool(calcSingleFreq), "Lvls via Indiv Freq", "Lvls via FFT");			
			guiObjs_Numeric[gIDX_curSong].setDispText("MP3 Clip");
			break;}
		}		
	}//updateButtons
	
	@Override
	protected void initMe() {
		//build ball object
		//scale z val == 1 is sphere, <1 is ellipsoid
		ball = new myDancingBall(pa, this, "Ball for zone : " + name,new myVectorf(0,0,0),ballRadius, 1.0f);
		//piano to display on size of window
		dispPiano = new myPianoObj(fillClr, rectDim);		//start with 52 white keys (full keyboard)
		//dispPiano.updateGridXandY(false, rectDim);
		//called once
		initPrivFlags(numPrivFlags);
		//this window is runnable
		setFlags(isRunnable, true);
		//this window uses a customizable camera
		setFlags(useCustCam, true);
		//initially start with the following priv flags set
		setAllPrivFlags(new int[] {useForcesForBall,sendAudioToBall,calcSingleFreq, showPianoKbd,showMelodyTrail }, true);
		
		custMenuOffset = uiClkCoords[3];	//495
		rebuildDancer();		
		//audMgr needs to be built after dispPiano and flags values have been set	
		//audMgr will handle all audio tasks - playing, analysis, etc.
		audMgr = new myAudioManager(pa, this);	
	}//initMe	
	
	/**
	 * build dancing ball
	 */	
	private void rebuildDancer() {
		//ball.buildVertsAndNorms(ballRadius, ballNumVerts, getPrivFlags(randVertsForSphere));
		ball.rebuildMe();
	}//buildDancingBall
	
	//set ball reset all verts in zones being displaced, before changing to a new zone
	private void resetDancerDisplacement() {
		ball.resetConfig();//ball.resetVertLocs(zoneToShow, zoneMmbrToShow, true);//removed this to make general
	}	
	
	//set once ball is either being rebuilt or is finished being built
	public void setBallIsMade(boolean val) {	setPrivFlags(ballIsMade, val);}
	public boolean getBallIsMade() {	return getPrivFlags(ballIsMade);}
	public boolean getStimZoneMates() {	return getPrivFlags(stimZoneMates);}
	
	//display flags that are mutually exclusive - only one of these states can be true at a time, although all can be false
	private final int[] mutExclFlags = new int[] {showAllBandRes,showZoneBandRes,showIntrvls,showVolLvls};
	
	//set all passed flags to val except _exclFlIdx - this will have been called by _exclFlIdx, so don't set it
	private void setAllButPrivFlags(int _exclFlIdx, int[] _flags, boolean _val) {
		for(int i=0;i<_flags.length;++i) {if(_flags[i]!= _exclFlIdx) {setPrivFlags(_flags[i], _val);}}
	}//setAllButPrivFlags
	
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
				rebuildDancer();
				break;}
			case showVertNorms			: {break;}
			case ballIsMade				: {//changes when notification is received that ball's state has changed
				break;}
			case showZones				: {break;}
			case stimZoneMates			: {break;}
			//audio manager flags -> send to audio manager
			case playMP3Vis				: {
				//POTENTIALLY CIRCULAR REFS BELOW - if pauseAudio can call setPrivFlags then infinite loop is possible
				if(val) {	boolean res = audMgr.startAudio(); if (!res) {setPrivFlags(playMP3Vis, false);}}//if audio doesn't start playing turn play button off
				else {		audMgr.pauseAudio();}
				break;}
			case sendAudioToBall 		: {break;}
			case useForcesForBall		: {
				//1 : force/mass-spring, 0 : kinematic
				sendStimTypeToBall(val ? 1 : 0);
				break;}
			case usePianoTune 			: {break;} 
			//these three are mutually exclusive
			case showZoneBandRes		: {	if(val) {setAllButPrivFlags(idx, mutExclFlags, false);}break;}//
			case showAllBandRes			: {	if(val) {setAllButPrivFlags(idx, mutExclFlags, false);}break;}//
			case showIntrvls			: { if(val) {setAllButPrivFlags(idx, mutExclFlags, false);}setPrivFlags(showFreqLbls,false);break;}//
			case showVolLvls			: { if(val) {setAllButPrivFlags(idx, mutExclFlags, false);}setPrivFlags(showFreqLbls,false);break;}//
			case calcSingleFreq 		: {	break;}
			case useSumLvl 				: {	break;}
			case showPianoKbd			: {//show piano kbd
				//if(!val) {	setPrivFlags(showPianoNoteNames, false);}
				break;}
			case showPianoNoteNames 	: {//show names of notes on displayed piano
				
				break;}
			//case showEachOctave : {				if(val) {setPrivFlags(calcSingleFreq, true);}break;}
			case showMelodyTrail :{//show trail of melody, else show key levels, when showing piano and not showing all freq response
				break;}
		}		
	}//setPrivFlags	
	
	
//	
//	//clear pre-preprocessing flag - call from thread executing processing
//	private void clearPreProcMidi() {
//		//when finished set : 
//		setPrivFlags(procMidiData, false);		
//	}
//	
//	//launch preprocessing of midi data
//	private void preprocMidiData() {
//		//fire and forget midi processing
//		
//		
//	}
	
	//send current desired stimualtion type to ball based on UI input
	private void sendStimTypeToBall(int stimType) {
		ball.setZoneModStimType(stimType);
	}
	
	//initialize structure to hold modifiable menu regions
	@Override
	protected void setupGUIObjsAras(){	
		//pa.outStr2Scr("setupGUIObjsAras start");
		guiMinMaxModVals = new double [][]{
			{0,1.0f,.001f},						//timestep           		gIDX_TimeStep 	
			{1000,50000,10},						//# of vertices
			{50,1000,10},						//ball at-rest radius
			{5, 100, 5},						//min neighborhood size fraction of number of verts			
			{0,numZones-1,1},					//zone to show if showing zones on sphere
			{0,10000,1},						//zone member to show if showing zones on sphere (% list size, so can be huge)
			{0.0, myAudioManager.songDirList.length-1, 0.1}, //either "midi" or "mp3" - audio type either midi sequence or audio file
			{0.0, myAudioManager.songBanks[(int)uiVals[gIDX_curSongDir]].length-1, 0.1},	//song bank selected
			{0.0, myAudioManager.songList[(int)uiVals[gIDX_curSongDir]][(int)uiVals[gIDX_curSongBank]].length-1, 0.1},	//song/clip selected - start with initial bank
			{1,10,1},							//Top # of notes to show per lvl mapping result
			{0,100.0f,1.0f},					//% of max volume to use as cutuff, below which notes will not display
			{0,dftResTypeToShow.length-1,1},							//type of results to display 
			{0.0,5.0,0.1},						//absolute noise gate/noise floor
			{0,dftCalcTypeToUse.length-1,1},
			{0.0, fftWinNames.length-1, 1.0},	//window function selected
			{1,myAudioManager.maxBinSize, 1.0},
		};		//min max mod values for each modifiable UI comp	

		guiStVals = new double[]{
			uiVals[gIDX_TimeStep],	
			uiVals[gIDX_NumVerts],
			uiVals[gIDX_BallRad],
			uiVals[gIDX_minVertNBHD],
			uiVals[gIDX_zoneToShow],
			uiVals[gIDX_zoneMmbrToShow],
			uiVals[gIDX_curSongDir],
			uiVals[gIDX_curSongBank],
			uiVals[gIDX_curSong],
			uiVals[gIDX_numNotesByLvl],
			uiVals[gIDX_audThresh],
			uiVals[gIDX_typeDFTToShow],
			uiVals[gIDX_noiseGate],
			uiVals[gIDX_DFTCalcType],
			uiVals[gIDX_winSel],
			uiVals[gIDX_midiVolDispBin]
		};								//starting value
		
		guiObjNames = new String[]{
				"Time Step",
				"# of Vertices in Ball",
				"Rest Radius of Ball",
				"Min Ratio # Verts in NBHD",
				"Zone to show on Sphere",
				"Zone Member to Show",
				"Audio Type",
				"Song Bank",
				"MP3 Clip",
				"# Max Lvl Keys to Show",
				"% of Max Key Lvl as Disp Thresh",
				"DFT Result Types To Show",
				"Noise Gate Lvl",
				"DFT Function To Use",
				"FFT Window func",
				"Midi Volume Display Resolution"
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
			{true, true, true},
			{true, true, true},
			{true, false, true},
			{false, false,true},
			{true, true, true},
			{false,false,true},
			{true, true, true},
			{true, true, true},
			{true, false, true}
		};						//per-object  list of boolean flags
		
		//since horizontal row of UI comps, uiClkCoords[2] will be set in buildGUIObjs		
		guiObjs_Numeric = new myGUIObj[numGUIObjs];			//list of modifiable gui objects
		if(numGUIObjs > 0){
			buildGUIObjs(guiObjNames,guiStVals,guiMinMaxModVals,guiBoolVals,new double[]{xOff,yOff});			//builds a horizontal list of UI comps
		}
//		setupGUI_XtraObjs();
	}//setupGUIObjsAras
	
//	//setup UI object for song slider
//	private void setupGUI_XtraObjs() {
//		double stClkY = uiClkCoords[3], sizeClkY = 3*yOff;
//		guiObjs_Numeric[songTransIDX] = new myGUIBar(pa, this, songTransIDX, "MP3 Transport for ", 
//				new myVector(0, stClkY,0), new myVector(uiClkCoords[2], stClkY+sizeClkY,0),
//				new double[] {0.0, 1.0,0.1}, 0.0, new boolean[]{false, false, true}, new double[]{xOff,yOff});	
//		
//		//setup space for ui interaction with song bar
//		stClkY += sizeClkY;				
//		uiClkCoords[3] = stClkY;
//	}
	
	@Override
	protected void setUIWinVals(int UIidx) {
		float val = (float)guiObjs_Numeric[UIidx].getVal();
		float oldVal = uiVals[UIidx];
		//int ival = (int)val;
		if(val != uiVals[UIidx]){//if value has changed...
			uiVals[UIidx] = val;
			switch(UIidx){		
			case gIDX_TimeStep 			:{
				if(val != deltaT){deltaT = val;}
				break;}
			case gIDX_NumVerts			:{
				ballNumVerts = (int)val;
				rebuildDancer();
				break;}
			case gIDX_BallRad		:{
				ballRadius = (int)val;
				rebuildDancer();
				break;}
			case gIDX_minVertNBHD		:{
				minVInNBD = (int)val;
				rebuildDancer();
				break;}
			case gIDX_zoneToShow 	:{
				resetDancerDisplacement();
				zoneToShow = (int)val;
				break;}
			case gIDX_zoneMmbrToShow :{
				resetDancerDisplacement();
				zoneMmbrToShow = ((int)val) % ball.getZoneSize(zoneToShow);
				//reset UI display value to be zoneMmbrToShow
				guiObjs_Numeric[UIidx].setVal(zoneMmbrToShow);
				break;}
			case gIDX_curSongDir :{//changing current song type
				//change song type
				setPrivFlags(playMP3Vis,false);//turn off playing
				uiVals[gIDX_curSongDir] = val % myAudioManager.songDirList.length;
				int curSongDir=(int)uiVals[gIDX_curSongDir];
				//change bank display and max vals
				//change current song bank value to be legal within song list for this type
				uiVals[gIDX_curSongBank] %= myAudioManager.songList[curSongDir].length;
				//change song list dropdown max to be this bank's song list length-1
				guiObjs_Numeric[gIDX_curSongBank].setNewMax(myAudioManager.songList[curSongDir].length-1);
				//change current song
				boolean changed = audMgr.changeCurrentSong(curSongDir,(int)uiVals[gIDX_curSongBank],(int)uiVals[gIDX_curSong]);//changeCurrentSong((int)val);	
				if(!changed) {//return to previous value
					uiVals[gIDX_curSongDir] = oldVal;
					curSongDir=(int)uiVals[gIDX_curSongDir];
					uiVals[gIDX_curSongBank] %= myAudioManager.songList[curSongDir].length;
					guiObjs_Numeric[gIDX_curSongBank].setNewMax(myAudioManager.songList[curSongDir].length-1);
				}
				ball.resetConfig();	
				break;}
			case gIDX_curSongBank : {//changing current bank within song type
				//need to set songs available from this bank
				int curSongDir=(int)uiVals[gIDX_curSongDir];
				//change banks - stop music
				setPrivFlags(playMP3Vis,false);//turn off playing
				uiVals[gIDX_curSongBank] = val % myAudioManager.songList[curSongDir].length;
				int curSongBank = (int)uiVals[gIDX_curSongBank];
				//change current song idx value to be legal within song list for this bank
				uiVals[gIDX_curSong] %= myAudioManager.songList[curSongDir][curSongBank].length;
				//change song list dropdown max to be this bank's song list length-1
				guiObjs_Numeric[gIDX_curSong].setNewMax(myAudioManager.songList[curSongDir][curSongBank].length-1);
				boolean changed = audMgr.changeCurrentSong(curSongDir,curSongBank,(int)uiVals[gIDX_curSong]);
				if(!changed) {//if song wasn't changed, go back to old bank value
					uiVals[gIDX_curSongBank] = oldVal;
					curSongBank = (int)uiVals[gIDX_curSongBank];
					uiVals[gIDX_curSong] %= myAudioManager.songList[curSongDir][curSongBank].length;
					guiObjs_Numeric[gIDX_curSong].setNewMax(myAudioManager.songList[curSongDir][curSongBank].length-1);
				}
				ball.resetConfig();	
				break;}
			case gIDX_curSong 	: {//changing current song within bank within type
				boolean changed = audMgr.changeCurrentSong((int)uiVals[gIDX_curSongDir],(int)uiVals[gIDX_curSongBank],(int)uiVals[gIDX_curSong]);//changeCurrentSong((int)val);
				if(!changed) {
					uiVals[gIDX_curSong] = oldVal;
				}
				ball.resetConfig();
				break;}
			case gIDX_numNotesByLvl :{//send value to audioMgr
				myAudioManager.numNotesToShow = (int)(uiVals[gIDX_numNotesByLvl]);				
				break;}
			case gIDX_audThresh : {
				myAudioManager.audThreshold = uiVals[gIDX_audThresh]/100.0f;	//convert % to multiplier
				break;}
			case gIDX_typeDFTToShow :{
				//setPrivFlags(calcSingleFreq, true);//force to be true if this changes
				myAudioManager.dftThdResToShow = (int)(uiVals[gIDX_typeDFTToShow]);	
				break;}
			case gIDX_DFTCalcType : {
				myAudioManager.calcFuncToUse = (int)(uiVals[gIDX_DFTCalcType]);	
				break;}
			case gIDX_noiseGate : {
				myAudioManager.noiseGateLvl = uiVals[gIDX_noiseGate];
				break;}
			case gIDX_winSel		: {
				audMgr.changeCurrentWindowfunc((int)val);	
				break;}
			case gIDX_midiVolDispBin :{
				audMgr.curBinSize = (int)val;
			}
			default : {break;}
			}
		}
	}
	//if any ui values have a string behind them for display
	@Override
	protected String getUIListValStr(int UIidx, int validx) {
		int curSongDirIDX = (int)uiVals[gIDX_curSongDir],
			curSongBankIDX = (int)uiVals[gIDX_curSongBank];
			
		switch(UIidx){
			case gIDX_curSongDir :{ return myAudioManager.songDirList[validx %myAudioManager.songDirList.length];}
			case gIDX_curSongBank :{ return myAudioManager.songBanks[curSongDirIDX][validx %myAudioManager.songBanks[curSongDirIDX].length];}
			case gIDX_curSong : {return myAudioManager.songList[curSongDirIDX][curSongBankIDX][validx%myAudioManager.songList[curSongDirIDX][curSongBankIDX].length];}
			case gIDX_typeDFTToShow : {return dftResTypeToShow[(validx % dftResTypeToShow.length)];}
			case gIDX_DFTCalcType : {return dftCalcTypeToUse[(validx % dftCalcTypeToUse.length)];}
					
//					getPrivFlags(this.usePianoNoteFiles) ? 
//							pianoNoteList[(validx % pianoNoteList.length)] :	
//							songList[(validx % songList.length)]; }
			case gIDX_winSel  : {return fftWinNames[(validx % fftWinNames.length)]; }
			default : {break;}
		}
		return "";
	}
	
	public float getTimeStep(){
		return uiVals[gIDX_TimeStep] * timeStepMult;
	}
	
	//save the timing of a tapped beat of a certain type - map to zones for now
	//when this is called, key was pressed to signify the location of a beat of type key.
	//this will happen multiple times, and the average of the taps will represent the timing of the beat for type key
	public void saveTapBeat(int key) {
		if (getPrivFlags(useHumanTapBeats)) {audMgr.saveTapBeat(key);}	
//		else {//if not enabled (not shown) then reset tapBeats struct (?)
//			audMgr.initTapBeatStructs();
//		}		

	}//saveTapBeat
	@Override
	public void initDrwnTrajIndiv(){}
	
//	public void setLights(){
//		pa.ambientLight(102, 102, 102);
//		pa.lightSpecular(204, 204, 204);
//		pa.directionalLight(180, 180, 180, 0, 1, -1);	
//	}	
	//overrides function in base class mseClkDisp
	@Override
	public void drawTraj3D(float animTimeMod,myPoint trans){}//drawTraj3D	
	//set camera to either be global or from pov of one of the boids
	@Override
	protected void setCameraIndiv(float[] camVals){
		pa.camera(camVals[0],camVals[1],camVals[2],camVals[3],camVals[4],camVals[5],camVals[6],camVals[7],camVals[8]);      
		// puts origin of all drawn objects at screen center and moves forward/away by dz
		pa.translate(camVals[0],camVals[1],(float)dz); 
	    pa.setCamOrient();	
	}//setCameraIndiv

	@Override
	//draw 2d constructs over 3d area on screen
	protected void drawOnScreenStuffPriv(float modAmtMillis) {
		//draw all 2d screen audio
		if (getPrivFlags(showPianoKbd)){
			dispPiano.drawMe(pa, getPrivFlags(showPianoNoteNames));
		}
		audMgr.drawScreenData(modAmtMillis);		
	}//drawOnScreenStuff
	@Override
	//put information in right-side hideable window for this window
	protected void drawRightSideInfoBar(float modAmtMillis) {
		
		
	}

	@Override
	protected void drawMe(float animTimeMod) {
//		curMseLookVec = pa.c.getMse2DtoMse3DinWorld(pa.sceneOriginVals[pa.sceneIDX]);			//need to be here
//		curMseLoc3D = pa.c.getMseLoc(pa.sceneOriginVals[pa.sceneIDX]);
		//int stVal = pa.millis(); //takes 0 millis to process audio data
		//if(getPrivFlags(playMP3Vis)) {
			boolean updateBall = audMgr.processAudioData(animTimeMod);//get next set of audio data to process
			if(updateBall) {		ball.setFreqVals(audMgr.bandRes);	}
		//}
		
		//pa.outStr2Scr("took : " + (pa.millis() - stVal) + " millis to processAudioData()");
		//int stVal = pa.millis();//takes around 90 millis to draw ball
		ball.drawMe(animTimeMod);//,zoneToShow,getPrivFlags(showZones), getPrivFlags(stimZoneMates), getPrivFlags(showVertNorms));
		//pa.outStr2Scr("took : " + (pa.millis() - stVal) + " millis to ball.drawMe()");
	}//drawMe	
	
	@Override
	public void drawCustMenuObjs(){
		pa.pushMatrix();				pa.pushStyle();		
		//all sub menu drawing within push mat call
		pa.translate(5,custMenuOffset+yOff);
		//draw any custom menu stuff here
		pa.popStyle();					pa.popMatrix();		
	}//drawCustMenuObjs
	
	@Override
	//modAmtMillis is time passed per frame in milliseconds
	protected boolean simMe(float modAmtMillis) {//run simulation
		if(!getPrivFlags(ballIsMade)) {return true;}
		//int stVal = pa.millis();//takes around 5 millis to sim ball
		if(getPrivFlags(sendAudioToBall)) {
			//ball.stimulateBall(getPrivFlags(useForces), modAmtMillis);
			ball.setSimVals();
			ball.simMe( modAmtMillis,  getPrivFlags(stimWithTapBeats),  getPrivFlags(useForcesForBall)); 
		}
		//set to true to finish simulation
		return false;
		//pa.outStr2Scr("took : " + (pa.millis() - stVal) + " millis to ball simulate");
	}//simMe
	
	
	@Override
	protected void closeMe() {
		//things to do when swapping this window out for another window - release objects that take up a lot of memory, for example.
	}
	
	
	@Override
	protected void showMe() {
		//things to do when swapping into this window - reinstance released objects, for example.
		pa.setMenuDbgBtnNames(menuDbgBtnNames);
		pa.setMenuFuncBtnNames(menuFuncBtnNames);
	}

	
	@Override
	//stopping simulation
	protected void stopMe() {System.out.println("Stop");	resetDancerDisplacement();}
	
	//clear btn state for building audio file IO tree - runs in threads.  probably finishes before mouse release
	public void clearFuncBtnSt_BuildAudioFileIO() {
		clearFuncBtnState(0, true);
	}
	
	//clear btn state for loading midi data - won't finish before mouse is relased - need to turn button off in UI
	public void clearFuncBtnSt_BuildMidiData() {
		clearFuncBtnState(1,true);
	}
	//clear btn state for processing midi data - won't finish before mouse is relased - need to turn button off in UI
	public void clearFuncBtnSt_ProcMidiData() {
		clearFuncBtnState(2,true);
	}
	//clear btn state for processing midi data - won't finish before mouse is relased - need to turn button off in UI
	public void clearFuncBtnSt_SaveMidiData() {
		clearFuncBtnState(3,true);
	}
	
	//custom functions launched by UI input
	//if launching threads for custom functions, need to remove clearFuncBtnState call in function below and call clearFuncBtnState when thread ends
	private void custFunc0(){
		audMgr.buildAudioFileIO();		
	}			
	private void custFunc1(){
		audMgr.loadMidiData();	
	}	
	
	private void custFunc2(){	
		audMgr.procSaveMidiData();	
	}			
	private void custFunc3(){	
		clearFuncBtnState(3,false);
	}			
	private void custFunc4(){	
		//custom function code here
		clearFuncBtnState(4,false);
	}		
	@Override
	public void clickFunction(int btnNum) {
		pa.outStr2Scr("click cust function in "+name+" : btn : " + btnNum);
		switch(btnNum){
			case 0 : {	custFunc0();	break;}
			case 1 : {	custFunc1();	break;}
			case 2 : {	custFunc2();	break;}
			case 3 : {	custFunc3();	break;}
			case 4 : {	custFunc4();	break;}
			default : {break;}
		}	
	}		//only for display windows
	
	//debug function
	//if launching threads for debugging, need to remove clearDBGState call in function below and call clearDBGState when thread ends
	private void dbgFunc0(){		
		ball.debug0();//display ball's zone's x,y,z for each zone type's zones	

		clearDBGBtnState(0,false);
	}	
	private void dbgFunc1(){	
		pa.checkMemorySetup();
		//dbg code here
		clearDBGBtnState(1,false);
	}	
	private void dbgFunc2(){		
		//dbg code here
		clearDBGBtnState(2,false);
	}	
	private void dbgFunc3(){		
		//dbg code here
		clearDBGBtnState(3,false);
	}	

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
	}//clickDebug
	
	@Override
	public void hndlFileLoadIndiv(String[] vals, int[] stIdx) {}
	@Override
	public List<String> hndlFileSaveIndiv() {List<String> res = new ArrayList<String>();return res;}
	@Override
	protected void processTrajIndiv(myDrawnSmplTraj drawnNoteTraj){	}
	@Override
	protected boolean hndlMouseMoveIndiv(int mouseX, int mouseY, myPoint mseClckInWorld){return false;}
	//alt key pressed handles trajectory
	//cntl key pressed handles unfocus of spherey
	@Override
	protected boolean hndlMouseClickIndiv(int mouseX, int mouseY, myPoint mseClckInWorld, int mseBtn) {	boolean res = checkUIButtons(mouseX, mouseY);	return res;}//hndlMouseClickIndiv
	@Override
	protected boolean hndlMouseDragIndiv(int mouseX, int mouseY, int pmouseX, int pmouseY, myPoint mouseClickIn3D, myVector mseDragInWorld, int mseBtn) {boolean res = false;return res;}	
	@Override
	protected myPoint getMsePtAs3DPt(int mouseX, int mouseY){return pa.P(mouseX,mouseY,0);}
	@Override
	protected void snapMouseLocs(int oldMouseX, int oldMouseY, int[] newMouseLoc) {}	
	@Override
	protected void hndlMouseRelIndiv() {}
	@Override
	protected void endShiftKey_Indiv() {}
	@Override
	protected void endAltKey_Indiv() {}
	@Override
	protected void endCntlKey_Indiv() {}
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
	protected void resizeMe(float scale) {		dispPiano.updateGridXandY( rectDim);		}

}//DancingBallWin



