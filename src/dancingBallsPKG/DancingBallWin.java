package dancingBallsPKG;

//
import java.util.*;


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
	//idxs - need one per object
	public final static int
		gIDX_TimeStep 		= 0,
		gIDX_NumVerts		= 1,
		gIDX_BallRad		= 2,
		gIDX_minVertNBHD	= 3,
		gIDX_zoneToShow		= 4,
		gIDX_zoneMmbrToShow = 5,
		gIDX_curSongBank	= 6,
		gIDX_curSong 		= 7,
		gIDX_winSel 		= 8;
	//initial values - need one per object
	public float[] uiVals = new float[]{
			deltaT,
			ballNumVerts,
			ballRadius,
			minVInNBD,
			zoneToShow,
			zoneMmbrToShow,
			0,	//song bank in audMgr
			1,//songIDX, in audMgr			
			0//	curWindowIDX in audMgr	
	};			//values of 8 ui-controlled quantities

	public final int numGUIObjs = uiVals.length;											//# of gui objects for ui	
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
			showPianoNotes		= 16,		//display piano notes being played
			calcSingleFreq		= 17,		//analyze signal with single frequencies
			showEachOctave 		= 18; 	
	public static final int numPrivFlags = 19;
	
	//piano display
	public float whiteKeyWidth = 78, bkModY;				//how long, in pixels, is a white key, blk key is 2/3 as long
	//displayed piano
	public int gridX, gridY;						//pxls per grid box
	
	//offset to bottom of custom window menu 
	private float custMenuOffset;
	//display names of fft windows
	public String[] windowNames = new String[]{"NONE","BARTLETT","BARTLETTHANN","BLACKMAN","COSINE","GAUSS","HAMMING","HANN","LANCZOS","TRIANGULAR"};
	
	public DancingBallWin(DancingBalls _p, String _n, int _flagIdx, int[] fc, int[] sc, float[] rd, float[] rdClosed, String _winTxt, boolean _canDrawTraj) {
		super(_p, _n, _flagIdx, fc, sc, rd, rdClosed, _winTxt, _canDrawTraj);
		float stY = rectDim[1]+rectDim[3]-4*yOff,stYFlags = stY + 2*yOff;
		trajFillClrCnst = DancingBalls.gui_DarkCyan;		//override this in the ctor of the instancing window class
		trajStrkClrCnst = DancingBalls.gui_Cyan;
		super.initThisWin(_canDrawTraj, true, false);
	}//DancingBallWin	
	public void updateGridXandY(boolean resize){
		gridX = (int)(rectDim[2] * gridXMult);
		gridY = (int)(rectDim[3] * gridYMult);
		bkModY = .3f * gridY;
		if(resize){
			dispPiano.updateDims(gridX, gridY, new float[]{0, topOffY, whiteKeyWidth, 52 * gridY}, rectDim);
		}
	}//updateGridXandY
	public static int calcGridWidth(float winWidth){return (int)(winWidth*gridXMult);}
	public static int calcGridHeight(float winHeight){return (int)(winHeight*gridYMult);}
	
	@Override
	//initialize all private-flag based UI buttons here - called by base class
	public void initAllPrivBtns(){
		truePrivFlagNames = new String[]{								//needs to be in order of privModFlgIdxs
				"Debugging","Mod DelT By FRate","Random Ball Verts","Showing Vert Norms","Showing Zones", 
				"Stim Zone and Mate", "Playing MP3","Mass-Spring Ball", "Dancing", 
				"Stim Ball W/Beats","Showing Beats","Use Human Tap Beats", 
				"Showing Ctr Freq Vals","Showing Zone EQ", "Showing All Band Eq","Showing Piano","Showing Per Thd Note","Note Lvls w/Indiv F"	
		};
		falsePrivFlagNames = new String[]{			//needs to be in order of flags
				"Enable Debug","Fixed DelT","Uniform Ball Verts","Hiding Vert Norms", "Hiding Zones",
				"Stim Only Zones","Stopped MP3","Kinematics Ball","Not Dancing", 
				"Stim Ball W/Audio","Hiding Beats","Use Detected Beats",  
				"Hiding Ctr Freq Vals", "Hiding Zone EQ", "Hiding All Band Eq", "Hiding Piano","Showing One Note", "Note Lvls w/FFT"
		};
		privModFlgIdxs = new int[]{
				debugAnimIDX, modDelT,randVertsForSphere,showVertNorms,showZones,
				stimZoneMates,playMP3Vis, useForcesForBall, sendAudioToBall,  
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
		//called once
		initPrivFlags(numPrivFlags);
		//this window is runnable
		setFlags(isRunnable, true);
		//this window uses a customizable camera
		setFlags(useCustCam, true);
		//initially start with the following priv flags set
		setAllPrivFlags(new int[] {useForcesForBall,sendAudioToBall,calcSingleFreq}, true);
		
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
		ball.buildVertsAndNorms(ballRadius, ballNumVerts, getPrivFlags(randVertsForSphere));
	}//buildDancingBall
	
	//set ball reset all verts in zones being displaced, before changing to a new zone
	private void resetDancerDisplacement() {
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
				rebuildDancer();
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
//			case usePianoNoteFiles 		: {//change display to be list of piano note files
//				setPrivFlags(playMP3Vis,false);//turn off playing
//				if (val) {//use piano notes files
//					guiObjs[gIDX_curSong].setNewMax(pianoNoteList.length-1);
//					audMgr.songIDX %= pianoNoteList.length;
//				} else {//use song files
//					guiObjs[gIDX_curSong].setNewMax(songList.length-1);			
//					audMgr.songIDX %= songList.length;
//				}				
//				break;}
			case sendAudioToBall 		: {break;}
			case useForcesForBall		: {
				//1 : force/mass-spring, 0 : kinematic
				sendStimTypeToBall(val ? 1 : 0);
				break;}
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
	
	//send current desired stimualtion type to ball based on UI input
	private void sendStimTypeToBall(int stimType) {
		ball.setZoneModStimType(stimType);
	}
	
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
			{0.0, myAudioManager.songBanks.length-1, 0.1},	//song bank selected
			{0.0, myAudioManager.songList[(int)uiVals[gIDX_curSongBank]].length-1, 0.1},	//song/clip selected - start with initial bank
			{0.0, windowNames.length-1, 1.0},	//window function selected
		};		//min max mod values for each modifiable UI comp	

		guiStVals = new double[]{
			uiVals[gIDX_TimeStep],	
			uiVals[gIDX_NumVerts],
			uiVals[gIDX_BallRad],
			uiVals[gIDX_minVertNBHD],
			uiVals[gIDX_zoneToShow],
			uiVals[gIDX_zoneMmbrToShow],
			uiVals[gIDX_curSongBank],
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
				"Song Bank",
				"MP3 Clip",
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
				zoneMmbrToShow = ((int)val) % ball.zonePoints[zoneToShow].length;
				//reset UI display value to be zoneMmbrToShow
				guiObjs[UIidx].setVal(zoneMmbrToShow);
				break;}
			case gIDX_curSongBank : {
				//change banks - stop music
				setPrivFlags(playMP3Vis,false);//turn off playing
				uiVals[UIidx] = val % myAudioManager.songList.length;
				//change current song idx value to be legal within song list for this bank
				uiVals[gIDX_curSong] %= myAudioManager.songList[(int)uiVals[UIidx]].length;
				//change song list dropdown max to be this bank's song list length-1
				guiObjs[gIDX_curSong].setNewMax(myAudioManager.songList[(int)uiVals[UIidx]].length-1);

				audMgr.changeCurrentSong((int)uiVals[UIidx],(int)uiVals[gIDX_curSong]);
				ball.resetVertLocs();	
				break;
			}
			case gIDX_curSong 	: {
				audMgr.changeCurrentSong((int)uiVals[gIDX_curSongBank],(int)uiVals[gIDX_curSong]);//changeCurrentSong((int)val);
				ball.resetVertLocs();
				break;}
			case gIDX_winSel		: {
				audMgr.changeCurrentWindowfunc((int)val);	
				break;}
			default : {break;}
			}
		}
	}
	//if any ui values have a string behind them for display
	@Override
	protected String getUIListValStr(int UIidx, int validx) {
		switch(UIidx){
			case gIDX_curSongBank :{ return myAudioManager.songBanks[validx %myAudioManager.songBanks.length];}
			case gIDX_curSong : {return myAudioManager.songList[(int)uiVals[gIDX_curSongBank]][validx];}
					
//					getPrivFlags(this.usePianoNoteFiles) ? 
//							pianoNoteList[(validx % pianoNoteList.length)] :	
//							songList[(validx % songList.length)]; }
			case gIDX_winSel  : {return windowNames[(validx % windowNames.length)]; }
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
		audMgr.saveTapBeat(key);	
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

	@Override
	//draw 2d constructs over 3d area on screen
	protected void drawOnScreenStuff(float modAmtMillis) {
		pa.pushMatrix();pa.pushStyle();
		//move to side of menu
		pa.translate(rectDim[0],0,0);
		//draw all 2d screen audio
		audMgr.drawScreenData(modAmtMillis);	
		pa.popStyle();pa.popMatrix();				
	}//drawOnScreenStuff
	
	@Override
	protected void drawMe(float animTimeMod) {
//		curMseLookVec = pa.c.getMse2DtoMse3DinWorld(pa.sceneCtrVals[pa.sceneIDX]);			//need to be here
//		curMseLoc3D = pa.c.getMseLoc(pa.sceneCtrVals[pa.sceneIDX]);
		//int stVal = pa.millis(); //takes 0 millis to process audio data
		boolean updateBall = audMgr.processAudioData(animTimeMod);//get next set of audio data to process
		if(updateBall) {		ball.setFreqVals(audMgr.bandRes);	}
		
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
	}//drawCustMenuObjs

	
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
		//TODO determine per-zone spring constants by using beat frequency in each zone (evolving)
		//1.1254 as zonefreq gives ks=50 in ball
		myBeat[] beats = audMgr.getBeats();
		for(int i=0;i<numZones;++i) {
			float btFreq = beats[i].getBeatFreq();
			//zoneFreqs[i] = (btFreq <= 0 ? 1.1254f : btFreq);//this value results in 50 ks
			zoneFreqs[i] = (btFreq <= 0 ? 2.0f : btFreq);//this value results in 158 ks
		}//
		//set ball zone spring constants TODO use beat frequencies
		ball.setZoneKs(zoneFreqs);
		
		//set all required values for ball stimulation
		ball.setBallSimVals(zoneToShow,zoneMmbrToShow,audMgr.beatDetRes,audMgr.lastBeatDetRes);		
	}//setBallSimulateVals
	
	@Override
	//modAmtMillis is time passed per frame in milliseconds
	protected void simMe(float modAmtMillis) {//run simulation
		if(!getPrivFlags(ballIsMade)) {return;}
		//int stVal = pa.millis();//takes around 5 millis to sim ball
		if(getPrivFlags(sendAudioToBall)) {
			//ball.stimulateBall(getPrivFlags(useForces), modAmtMillis);
			setBallSimulateVals();
			ball.simMe( modAmtMillis,  getPrivFlags(stimWithTapBeats),  getPrivFlags(useForcesForBall)); 
		}
		//pa.outStr2Scr("took : " + (pa.millis() - stVal) + " millis to ball simulate");
	}//simMe
	
	@Override
	protected void stopMe() {System.out.println("Stop");	resetDancerDisplacement();}
	//debug function
	public void dbgFunc0(){
		//display ball's zone's x,y,z for each zone type's zones
		ball.debugAllZones();
		//zones to use : 
	}	
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
	}//clickDebug
	
	@Override
	public void hndlFileLoadIndiv(String[] vals, int[] stIdx) {}
	@Override
	public List<String> hndlFileSaveIndiv() {List<String> res = new ArrayList<String>();return res;}
	@Override
	protected void processTrajIndiv(myDrawnSmplTraj drawnNoteTraj){	}
	@Override
	protected myPoint getMouseLoc3D(int mouseX, int mouseY){return pa.P(mouseX,mouseY,0);}
	@Override
	protected boolean hndlMouseMoveIndiv(int mouseX, int mouseY, myPoint mseClckInWorld){return false;}
	//alt key pressed handles trajectory
	//cntl key pressed handles unfocus of spherey
	@Override
	protected boolean hndlMouseClickIndiv(int mouseX, int mouseY, myPoint mseClckInWorld, int mseBtn) {	boolean res = checkUIButtons(mouseX, mouseY);	return res;}//hndlMouseClickIndiv
	@Override
	protected boolean hndlMouseDragIndiv(int mouseX, int mouseY, int pmouseX, int pmouseY, myPoint mouseClickIn3D, myVector mseDragInWorld, int mseBtn) {boolean res = false;return res;}	
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
	protected void resizeMe(float scale) {		updateGridXandY(true);		}
	@Override
	protected void closeMe() {}
	@Override
	protected void showMe() {}
}//DancingBallWin

