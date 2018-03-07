package dancingBallsPKG;

/**
 * this file holds all physically-based sim components : classes to implement forces, particles, colliders, springs.
 */

import java.util.*;
import org.jblas.DoubleMatrix;


public class myParticle {
	public final int ID;
	public static int IDgen = 0;

	public myVectorf initPos, initVel;
	//used for spring forces - last iteration's distance vector
	public myVectorf vecLOld;
	//reference to ks and kd values for spring attached to this particle
	public double[] kskdVals;
	//private boolean isSetKsKd;//make sure lowest-frequency zone dominates in ks/kd setting
	//array to keep around 
	public myVectorf[] aPosition, aVelocity, aForceAcc ,aOldPos, aOldVel,aOldForceAcc;

	public int curIDX;
	public static int curNext, curPrev;
	public int colVal;						//collision check value
	
	public static final int szAcc = 2;		//size of accumulator arrays
	
	public float mass = 1,origMass;
	public SolverType solveType;		//{GROUND, EXP_E, MIDPOINT, RK3, RK4, IMP_E, etc }
	public mySolver solver;

	public myParticle(myVectorf _iPos, myVectorf _iVel, myVectorf _iFrc, SolverType _styp) {
		ID = IDgen++;
		init(_iPos, _iVel, _iFrc, _styp);	
	}
	
	private void init(myVectorf _pos, myVectorf _velocity, myVectorf _forceAcc, SolverType _solv) {
		curIDX = 0;									//cycling ptr to idx in arrays of current sim values
		curNext = 1; 
		curPrev = 0;
		aPosition = new myVectorf[szAcc];
		aVelocity = new myVectorf[szAcc];
		aForceAcc = new myVectorf[szAcc];
		aOldPos = new myVectorf[szAcc];
		aOldVel = new myVectorf[szAcc];
		aOldForceAcc = new myVectorf[szAcc];
		for(int i=0;i<szAcc;++i){
			aPosition[i] = new myVectorf();
			aVelocity[i] = new myVectorf();
			aForceAcc[i] = new myVectorf();
			aOldPos[i] = new myVectorf();
			aOldVel[i] = new myVectorf();
			aOldForceAcc[i]	= new myVectorf();	
		}
		aPosition[0].set(_pos);
		aVelocity[0].set(_velocity);
		aForceAcc[0].set(_forceAcc);
		aOldPos[0].set(_pos);
		aOldVel[0].set(_velocity);
		aOldForceAcc[0].set(_forceAcc);
		//vector from old position to rest position
		vecLOld = new myVectorf(0,0,0);
		setOrigMass(mass);
		initPos = new myVectorf(_pos);
		initVel = new myVectorf(_velocity);
		solveType = _solv;
		solver = new mySolver( _solv);
		//isSetKsKd = false;
	}

	protected void setOrigMass(float _m) {
		mass = _m;
		origMass = _m;
	}
	//reset position to start position
	public void reset() {
		for(int i=0;i<szAcc;++i){
			aPosition[i].set(initPos);
			aVelocity[i].set(initVel);
			aForceAcc[i].set(0,0,0);
			aOldPos[i].set(initPos);
			aOldVel[i].set(initVel);
			aOldForceAcc[i].set(0,0,0);
		}
	}
	
//	public void setKsKdVals(double[] _zoneKsKdVals) {
//		if(!isSetKsKd) {
//		kskdVals = _zoneKsKdVals;
//		isSetKsKd = true;
//		}
//	}
	
	
//	public static void updateCurPtrs(){
//		curNext = (curIDX + 1) % szAcc; 
//		curPrev = 0;
//		
//	}
	public myVectorf springForce = new myVectorf(0,0,0);
	public void applyForce(myVectorf _force) {aForceAcc[curIDX]._add(_force);}//applyforce
	public void integAndAdvance(double deltaT){		
		//idxs 2 and 3 of tSt hold last iteration's pos and vel
		myVectorf[] tSt = new myVectorf[]{ aPosition[curIDX], aVelocity[curIDX], aOldPos[curIDX], aOldVel[curIDX]};	
		//idxs 2 and 3 of tStDot hold last iteration's vel and frc
		if(springForce.magn >0) {
			applyForce(springForce);
			springForce.set(0,0,0);
		}
		myVectorf[] tStDot = (mass == 1.0f ? 
				new myVectorf[]{ tSt[1],aForceAcc[curIDX],tSt[3], aOldForceAcc[curIDX]} : 
				new myVectorf[]{ tSt[1],myVectorf._div(aForceAcc[curIDX],mass),tSt[3], myVectorf._div(aOldForceAcc[curIDX],mass)});
		myVectorf[] tNSt = solver.Integrate(deltaT, tSt, tStDot);
		
		int oldTopIDX = curIDX;
		curIDX = (curIDX + 1) % szAcc; 
		aOldPos[curIDX].set(aPosition[oldTopIDX]);
		aPosition[curIDX].set(tNSt[0]);
		
		aOldVel[curIDX].set(aVelocity[oldTopIDX]);
		aVelocity[curIDX].set(tNSt[1]);
		
		aOldForceAcc[curIDX].set(aForceAcc[oldTopIDX]);
		aForceAcc[curIDX].set(0,0,0);			//clear out new head of force acc	
		//isSetKsKd = false;
	}
	
	//distance from rest
	public myVectorf delPos() {return myVectorf._sub(aPosition[curIDX], initPos);}
	public float[] delPosAra() {return new float[] {aPosition[curIDX].x - initPos.x, aPosition[curIDX].y - initPos.y, aPosition[curIDX].z - initPos.z};}
	//delta between two particle distances and velocities
	public myVectorf delPos(myParticle b) {return myVectorf._sub(aPosition[curIDX], b.aPosition[curIDX]);}
	public myVectorf delVel(myParticle b) {return myVectorf._sub(aVelocity[curIDX], b.aVelocity[curIDX]);}
	
	@Override
	public String toString(){
		String res = "ID : " + ID + "\tMass:"+mass+"\n";
		res +="\tPosition:"+aPosition[curIDX].toStrBrf()+"\n";
		res +="\tVelocity:"+aVelocity[curIDX].toStrBrf()+"\n";
		res +="\tCurrentForces:"+aForceAcc[curIDX].toStrBrf()+"\n";
		
		return res;		
	}
}//myParticle

//individual vertex of dancing sphere, rendered as point
class myRndrdPart extends myParticle{
	protected DancingBalls pa;
	protected myDancingBall ball;		//owning dancing ball
	public int[] color, origColor;	

	public myVectorf norm, dispNorm;

	public myRndrdPart(DancingBalls _pa, myDancingBall _ball, myVectorf _iPos, myVectorf _iVel, myVectorf _iFrc, SolverType _styp, myVectorf _norm, int _clr) {
		super(_iPos, _iVel, _iFrc, _styp);
		init(_pa, _ball, _norm, _clr);
	}
	//used to set initial mass at construction
	public myRndrdPart(DancingBalls _pa, myDancingBall _ball, myVectorf _iPos, myVectorf _iVel, myVectorf _iFrc, SolverType _styp, myVectorf _norm, int _clr, float _oMass) {
		super(_iPos, _iVel, _iFrc, _styp);
		init(_pa, _ball, _norm, _clr);
		setOrigMass(_oMass);
	}

	private void init(DancingBalls _pa, myDancingBall _ball, myVectorf _norm, int _clr) {
		pa = _pa;ball=_ball;
		norm = _norm;
		dispNorm = myVectorf._mult(norm, 10);
		color = pa.getClr(_clr, 255);
		origColor = pa.getClr(pa.gui_White, 255);		
	}
	
	
	@Override
	public void reset() {
		super.reset();
	}
	public myVectorf getCurPos() {
		return aPosition[curIDX];
	}
	//kinematic displacement by _stimVal
	public void displace(float _stimVal) {
		//aPosition[curIDX].set( myVectorf._add(initPos,myVectorf._mult(norm, _stimVal)));
		aPosition[curIDX]._add( myVectorf._mult(norm, _stimVal));
	}
	
	//use this to set to only a single force for springs, since multiple zones might affect a particle, we only want a single zone's spring to work on particle
	public void setSpringForce(float _stimVal) {
		springForce.set( myVectorf._mult(norm, _stimVal));
	}	
	
	//add force in direction of normal
	public void stimulateFrc(float _stimVal) {
		myVectorf _force = myVectorf._mult(norm, _stimVal);
		aForceAcc[curIDX]._add(_force);
	}
	private static int sphDet = 1;
	
	private void drawSphere() {pa.sphereDetail(sphDet);pa.sphere(DancingBalls.partRad);}	
	public void drawMe(){
		pa.pushMatrix();pa.pushStyle();
		pa.translate(aPosition[curIDX]);
		if(ID > 2000) {
			pa.stroke(255);
			pa.fill(255);			
		} else {
			pa.stroke(color[0],color[1],color[2],color[3]);
			pa.fill(color[0],color[1],color[2],color[3]);
		}
		//pa.setColorValFill(this.colVal+1, 255);
		drawSphere();
		pa.popStyle();pa.popMatrix();
	}
	public void drawMeWithColor(int clrIDX){

		pa.pushMatrix();pa.pushStyle();
		pa.translate(aPosition[curIDX]);
		pa.setColorValStroke(clrIDX);
		pa.setColorValFill(clrIDX);
		drawSphere();
		pa.popStyle();pa.popMatrix();
	}
	public void drawMeWithNorm(){
		pa.pushMatrix();pa.pushStyle();
		pa.translate(aPosition[curIDX]);
		pa.stroke(color[0],color[1],color[2],color[3]);
		pa.fill(color[0],color[1],color[2],color[3]);
		//pa.setColorValFill(this.colVal+1, 255);
		drawSphere();
		pa.stroke(255,255,255,255);
		pa.line(myPointf.ZEROPT, dispNorm);
		pa.popStyle();pa.popMatrix();
	}

}//class myRndrdPart

class mySolver {
	//value for rk4 general form
	public static int ID_gen = 0;
	public int ID;
	public SolverType intType;
	private myIntegrator intgrt;
	
	public mySolver(SolverType _type) {
		ID = ID_gen++;
		intType = _type;
		intgrt = buildIntegrator(2);
	}
	private myIntegrator buildIntegrator(double _lambda){
		switch (intType){
		case GROUND 	: {return new intGndTrth();}
		case EXP_E 		: {return new intExpEuler();}
		case MIDPOINT 	: {return new intMidpoint();}
		case RK3 		: {return new intRK3();}
		case RK4 		: {return new intRK4();}
		case IMP_E 		: {return new intImpEuler();}
		case TRAP 		: {return new intTrap();}
		case VERLET 	: {return new intVerlet();}
		case RK4_G 		: {return new intGenRK4(_lambda);}
		default 		: {return new intgrtNone();}
		}
	}	
	public myVectorf[] Integrate(double deltaT, myVectorf[] _state, myVectorf[] _stateDot){	return intgrt.Integrate(deltaT, _state, _stateDot);}	
}//mySolver class

abstract class myIntegrator{
	public static myVectorf gravVec = new myVectorf(DancingBalls.gravVec);
	public myIntegrator(){}
	protected myVectorf[] integrateExpE(double deltaT, myVectorf[] _state, myVectorf[] _stateDot){
		myVectorf[] tmpVec = new myVectorf[2];
		tmpVec[0] = myVectorf._add(_state[0], myVectorf._mult(_stateDot[0],deltaT));
		tmpVec[1] = myVectorf._add(_state[1], myVectorf._mult(_stateDot[1],deltaT));
		return tmpVec;
	}
	public abstract myVectorf[] Integrate(double deltaT, myVectorf[] _state, myVectorf[] _stateDot);
}

class intgrtNone extends myIntegrator{
	public intgrtNone(){}
	@Override
	public myVectorf[] Integrate(double deltaT, myVectorf[] _state, myVectorf[] _stateDot) {return _state;}
}//intgrtNone

class intGndTrth extends myIntegrator{
	public intGndTrth(){}
	@Override
	public myVectorf[] Integrate(double deltaT, myVectorf[] _state, myVectorf[] _stateDot) {
		myVectorf[] tmpVec = new myVectorf[]{
			myVectorf._add(_state[0], myVectorf._add(myVectorf._mult( _state[1], deltaT), myVectorf._mult(gravVec, (.5 * deltaT * deltaT)))),
			myVectorf._add(_state[1], myVectorf._mult(gravVec, deltaT))
		};
		return tmpVec;
	}
}//intGndTrth

class intExpEuler extends myIntegrator{
	public intExpEuler(){super();}
	@Override
	public myVectorf[] Integrate(double deltaT, myVectorf[] _state, myVectorf[] _stateDot) {
		myVectorf[] tmpVec = new myVectorf[]{
				myVectorf._add(_state[0], myVectorf._mult(_stateDot[0],deltaT)),
				myVectorf._add(_state[1], myVectorf._mult(_stateDot[1],deltaT))
		};
		return tmpVec;
	}
}//intExpEuler

class intMidpoint extends myIntegrator{
	public intMidpoint(){super();}
	@Override
	public myVectorf[] Integrate(double deltaT, myVectorf[] _state, myVectorf[] _stateDot) {
		myVectorf[] deltaXhalf = integrateExpE((deltaT *.5), _state, _stateDot);
		myVectorf[] tmpStateDot = new myVectorf[]{deltaXhalf[1],_stateDot[1]};
//
//		tmpStateDot[0] = deltaXhalf[1];			//new stateDot 0 term is v  @ t=.5 deltat, accel @ t = 0
//		tmpStateDot[1] = _stateDot[1];			//deltaV is the same acceleration = _stateDot[1]
		myVectorf[] tmpVec = integrateExpE(deltaT, _state, tmpStateDot);	//x0 + h xdot1/2
		return tmpVec;
	}
}//intMidpoint

class intVerlet extends myIntegrator{
	public static final double VERLET1mDAMP = .99999;          //1 minus some tiny damping term for verlet stability
	public intVerlet(){super();}
	@Override
	public myVectorf[] Integrate(double deltaT, myVectorf[] _state, myVectorf[] _stateDot) {
		double deltaSq = deltaT*deltaT;
		myVectorf[] tmpVec = new myVectorf[]{
			myVectorf._add(_state[0], myVectorf._add(myVectorf._mult(myVectorf._sub(_state[0], _state[2]), VERLET1mDAMP), myVectorf._mult(_stateDot[1], deltaSq))),          //verlet without velocity    
			myVectorf._add(_state[1], myVectorf._add(myVectorf._mult(myVectorf._sub(_state[1], _state[3]), VERLET1mDAMP), myVectorf._mult(myVectorf._sub(_stateDot[1], _stateDot[3]),deltaSq * .5)))           //verlet without velocity
		};
		return tmpVec;
	}
}//intVerlet

//////////////
///  all RK integrators assume constant force through timestep, which affects accuracy when using constraint and repulsive/attractive forces
////////////////

class intRK3 extends myIntegrator{
	public intRK3(){super();}
	@Override
	public myVectorf[] Integrate(double deltaT, myVectorf[] _state, myVectorf[] _stateDot) {

		myVectorf[] tmpVecState1 = integrateExpE(deltaT, _state, _stateDot);
		myVectorf[] tmpVecK1 = new myVectorf []{tmpVecState1[1],_stateDot[1]};

		myVectorf[] tmpVecState2 = integrateExpE((deltaT *.5), _state, tmpVecK1);
		myVectorf[]  tmpVecK2 = new myVectorf []{	tmpVecState2[1],tmpVecK1[1]	};	//move resultant velocity into xdot position
	
		myVectorf[] tmpVecState3 = integrateExpE(deltaT, _state, tmpVecK2);
		myVectorf[]  tmpVecK3 = new myVectorf []{	tmpVecState3[1], tmpVecK2[1]};			//tmpVecK3 should just be delta part of exp euler evaluation

		myVectorf[] tmpVec = new myVectorf []{
				myVectorf._add(_state[0], myVectorf._mult(myVectorf._div(myVectorf._add(myVectorf._add(tmpVecK1[0],myVectorf._mult(tmpVecK2[0],4)),tmpVecK3[0]), 6.0f), deltaT)),
				myVectorf._add(_state[1], myVectorf._mult(myVectorf._div(myVectorf._add(myVectorf._add(tmpVecK1[1],myVectorf._mult(tmpVecK2[1],4)),tmpVecK3[1]), 6.0f), deltaT))
		};

		return tmpVec;	}
}//intRK3

class intRK4 extends myIntegrator{
	public intRK4(){super();}
	@Override
	public myVectorf[] Integrate(double deltaT, myVectorf[] _state, myVectorf[] _stateDot) {

		//vector<Eigen::Vector3d> tmpVecState1 = IntegrateExp_EPerPart(deltaT, _state, _stateDot);
		myVectorf[] tmpVecState1 = integrateExpE(deltaT, _state, _stateDot);
		myVectorf[] tmpVecK1 = new myVectorf []{tmpVecState1[1],_stateDot[1]};

		myVectorf[] tmpVecState2 = integrateExpE((deltaT *.5), _state, tmpVecK1);
		myVectorf[]  tmpVecK2 = new myVectorf []{	tmpVecState2[1],tmpVecK1[1]	};	//move resultant velocity into xdot position
		
		myVectorf[] tmpVecState3 = integrateExpE((deltaT *.5), _state, tmpVecK2);
		myVectorf[]  tmpVecK3 = new myVectorf []{	tmpVecState3[1], tmpVecK2[1]};			//tmpVecK3 should just be delta part of exp euler evaluation

		myVectorf[] tmpVecState4 = integrateExpE(deltaT, _state, tmpVecK3);
		myVectorf[] tmpVecK4  = new myVectorf []{	tmpVecState4[1], tmpVecK3[1]};			//tmpVecK3 should just be delta part of exp euler evaluation

		myVectorf[] tmpVec = new myVectorf []{
				myVectorf._add(_state[0], myVectorf._mult(myVectorf._div(myVectorf._add(myVectorf._add(tmpVecK1[0],myVectorf._mult(myVectorf._add(tmpVecK2[0],tmpVecK3[0]),4)),tmpVecK4[0]), 6.0f), deltaT)),
				myVectorf._add(_state[1], myVectorf._mult(myVectorf._div(myVectorf._add(myVectorf._add(tmpVecK1[1],myVectorf._mult(myVectorf._add(tmpVecK2[1],tmpVecK3[1]),4)),tmpVecK4[1]), 6.0f), deltaT))
		};

		return tmpVec;
	}
}//intRK4

class intGenRK4 extends myIntegrator{
	private double lambda, lam2, invLam;
	public intGenRK4(double _l){super();lambda = _l; lam2 = lambda/2.0; invLam = 1.0/lambda;}
	@Override
	public myVectorf[] Integrate(double deltaT, myVectorf[] _state, myVectorf[] _stateDot) {
		
		myVectorf[] tmpVecState1 = integrateExpE(deltaT, _state, _stateDot);
		myVectorf[] tmpVecK1 = new myVectorf []{tmpVecState1[1],_stateDot[1]};

		myVectorf[] tmpVecState2 = integrateExpE((deltaT *.5), _state, tmpVecK1);
		myVectorf[]  tmpVecK2 = new myVectorf []{	tmpVecState2[1],tmpVecK1[1]	};	//move resultant velocity into xdot position

		myVectorf[] tmpVecK2a= new myVectorf []{				
				myVectorf._add(myVectorf._mult(_state[1],(.5 - invLam)),myVectorf._mult(tmpVecState2[1],invLam)),		//move resultant velocity into xdot position - general form uses 1 and 2
				tmpVecK1[1]};			//move acceleration into vdot position

		myVectorf[] tmpVecState3 = integrateExpE((deltaT *.5), _state, tmpVecK2a);
		myVectorf[]  tmpVecK3 = new myVectorf []{	tmpVecState3[1], tmpVecK2[1]};			//tmpVecK3 should just be delta part of exp euler evaluation

		myVectorf[]  tmpVecK3a= new myVectorf []{
				myVectorf._add(myVectorf._mult(tmpVecState2[1],(1 - lam2)),myVectorf._mult(tmpVecState3[1],lam2)),		//move resultant velocity into xdot position - general form uses 1 and 2
				tmpVecK2[1]};			//tmpVecK3 should just be delta part of exp euler evaluation

		myVectorf[] tmpVecState4 = integrateExpE(deltaT, _state, tmpVecK3a);
		myVectorf[] tmpVecK4  = new myVectorf []{	tmpVecState4[1], tmpVecK3[1]};			//tmpVecK3 should just be delta part of exp euler evaluation

//		tmpVec[0] = _state[0] + deltaT * ((tmpVecK1[0] + ((4 - lambda) * tmpVecK2[0]) + (lambda * tmpVecK3[0]) + tmpVecK4[0]) / 6.0);
//		tmpVec[1] = _state[1] + deltaT * ((tmpVecK1[1] + ((4 - lambda) * tmpVecK2[1]) + (lambda * tmpVecK3[1]) + tmpVecK4[1]) / 6.0);
		myVectorf[] tmpVec = new myVectorf []{
			myVectorf._add(_state[0], myVectorf._mult(myVectorf._div(myVectorf._add(myVectorf._add(tmpVecK1[0],myVectorf._add(myVectorf._mult(tmpVecK2[0], (4 - lambda)),myVectorf._mult(tmpVecK3[0], lambda))),tmpVecK4[0]), 6.0f), deltaT)),
			myVectorf._add(_state[1], myVectorf._mult(myVectorf._div(myVectorf._add(myVectorf._add(tmpVecK1[1],myVectorf._add(myVectorf._mult(tmpVecK2[1], (4 - lambda)),myVectorf._mult(tmpVecK3[1], lambda))),tmpVecK4[1]), 6.0f), deltaT))
		};		
		return tmpVec;
	}
}//intGenRK4

//not working properly - need to use conj grad-type solver - this is really semi-implicit
class intImpEuler extends myIntegrator{
	public intImpEuler(){super();}
	@Override
	public myVectorf[] Integrate(double deltaT, myVectorf[] _state, myVectorf[] _stateDot) {
		myVectorf[] tmpVec = new myVectorf[2];
		tmpVec[1] = myVectorf._add( _state[1],myVectorf._mult(_stateDot[1], deltaT));// + (deltaT * );//v : _stateDot[1] is f(v0)  we want f(v1) = f(v0) + delV * f'(v0) == delV = (1/delT * I - f'(v0))^-1 * f(v0)
		//have Vnew to calc new position
		tmpVec[0] = myVectorf._add(_state[0],myVectorf._mult(tmpVec[1], deltaT));//pos			//tmpVec[1] = v(t+dt)
		return tmpVec;
	}
}//intImpEuler

class intTrap extends myIntegrator{
	private double lambda;
	public intTrap(){super();}
	@Override
	public myVectorf[] Integrate(double deltaT, myVectorf[] _state, myVectorf[] _stateDot) {
		// TODO Auto-generated method stub
		myVectorf[] tmpVec = new myVectorf[2];
		tmpVec[1] = myVectorf._add( _state[1],myVectorf._mult(_stateDot[1], deltaT));		//assuming const accelerations allow use of _statDot[1] - otherwise need to calculate for f(v(t+dt))
		tmpVec[0] = myVectorf._add(_state[0],myVectorf._mult(myVectorf._add(myVectorf._mult(tmpVec[1],.5),myVectorf._mult(_state[1],.5)), deltaT));// _state[0] + (deltaT * ((.5*tmpVec[1]) + (.5 * _state[1])));		
		return tmpVec;
	}
}//intTrap

abstract class myForce {
	protected static DancingBalls pa;
	public static int ID_gen = 0;
	public int ID;
	public String name;
	public double constVal1;				//multiplicative constant to be applied to mass to find force/ ks
	public double constVal2;
	public myVectorf constVec;				//vector constant quantity, for use with gravity
	public ForceType ftype;

	public myForce(DancingBalls _p, String _n, double _k1, double _k2, myVectorf _constVec, ForceType _t){
  		pa = _p; 
		ID = ++ID_gen;
		name = new String(_n);
		constVal1 = _k1; 
		constVal2 = _k2;
		constVec = _constVec;		//torque-result force
		ftype = _t;
	}
	public myForce(DancingBalls _p,String _n, double _k1, double _k2) {this(_p, _n, _k1, _k2, new myVectorf(), ForceType.DAMPSPRING);}
	public myForce(DancingBalls _p,String _n, double _k) {this(_p, _n, _k * (_k>0 ? 1 : -1), 0, new myVectorf(), (_k>0) ? ForceType.REPL : ForceType.ATTR); ID = -1;}
	
	public void setConstVal1(double _c) {constVal1 = _c;}
	public void setConstVal2(double _c) {constVal2 = _c;}	
	
	public abstract myVectorf[] calcForceOnParticle(myParticle _p1, myParticle _p2, double d);// {S_SCALAR,S_VECTOR, ATTR, SPRING};
	@Override
	public String toString(){return "Force Name : " + name + " ID : " + ID + " Type : " + pa.ForceType2str[ftype.getVal()];}
}//myForce class

class mySclrForce extends myForce{
	// "scalar" force here means we derive the force by a particle-dependent scalar value, in this case mass against gravity vec 
	public mySclrForce(DancingBalls _p,String _n, myVectorf _G) { super(_p,_n, 0 ,0, new myVectorf(_G), ForceType.S_SCALAR);}	//	

	@Override
	//array returns up to 2 forces, one on p1, one on p2
	public myVectorf[] calcForceOnParticle(myParticle _p1, myParticle _p2, double d) {
		myVectorf[] result = new myVectorf[]{new myVectorf(),new myVectorf()};
		result[0] = myVectorf._mult(constVec,_p1.mass);
		return result;
	}
	@Override
	public String toString(){return super.toString() + "\tForce Vector :  " + constVec.toString();}
	
}//mySclrForce - scalar body-specific multiple of vector force

class myVecForce extends myForce{
	//vector here means we derive the force as a particle-dependent vector value, like velocity, against some scalar kd
	public myVecForce(DancingBalls _p,String _n, double _k) { super(_p,_n,_k,0, new myVectorf(), ForceType.S_VECTOR);}		//if drag, needs to be negative constant value	

	@Override
	public myVectorf[] calcForceOnParticle(myParticle _p1, myParticle _p2, double d) {
		myVectorf[] result = new myVectorf[]{new myVectorf(),new myVectorf()};
		result[0] = myVectorf._mult(_p1.aVelocity[_p1.curIDX], constVal1);//vector here means we derive the force as a particle-dependent vector value, velocity, against some scalar kd 
		return result;
	}
	@Override
	public String toString(){return super.toString() + "\tForce Scaling Constant :  " + String.format("%.4f",constVal1);}
	
}//myVecForce - vector body-specific quantity multiplied by scalar constant

class my2bdyForce extends myForce{
	//attractive/repulsive force
	public my2bdyForce(DancingBalls _p,  String _n, double _k,  ForceType _t) {
		super(_p, _n, _k, 0, new myVectorf(), _t);
	}
	public my2bdyForce(DancingBalls _p, String _n, double _k) {//passed k > 0 is repulsive force, k < 0 is attractive force
		this(_p, _n, Math.abs(_k), (_k>0) ? ForceType.REPL : ForceType.ATTR);
	}
	@Override
	public myVectorf[] calcForceOnParticle(myParticle _p1, myParticle _p2, double d) {
		myVectorf[] result = new myVectorf[]{new myVectorf(),new myVectorf()};
		myVectorf vecL;
		vecL = new myVectorf(_p2.aPosition[_p2.curIDX],_p1.aPosition[_p1.curIDX]);//vector from 2 to 1
		if (vecL.magn > pa.epsValCalc) {		
			double m1 = _p1.mass, m2 = _p2.mass;
			myVectorf lnorm = myVectorf._normalize(vecL);			//unitlength vector of l
			double fp = constVal1 * m1 * m2 / (vecL.sqMagn);		//from 2 to 1 if constVal > 0 (repulsive force)
			result[0] = myVectorf._mult(lnorm, fp);				//force applied to p1
			result[1] = myVectorf._mult(lnorm, -fp);				//force applied to p2
		}//only add force if magnitude of distance vector is not 0
		return result;
	}	
	@Override
	public String toString(){return super.toString() + "\tForce Scaling Constant :  " + String.format("%.4f",constVal1);}	
}

//spring force to rest position
class mySpringToRest extends myForce{
	//damped spring
	public mySpringToRest(DancingBalls _p,  String _n, double _k, double _k2,  ForceType _t) {super(_p, _n, _k, _k2, new myVectorf(), _t);	}
	public mySpringToRest(DancingBalls _p, String _n, double _k,double _k2) {this(_p, _n, _k, _k2, ForceType.DAMPSPRING);}
	
	//_p2 should be null, d should be 0, since we have it hardcoded to be to initPos
	@Override
	public myVectorf[] calcForceOnParticle(myParticle _p1, myParticle _p2, double d) {
		myVectorf[] result; //= new myVectorf[]{new myVectorf(),new myVectorf()};
		myVectorf vecL = new myVectorf(_p1.initPos,_p1.aPosition[_p1.curIDX]);//vector from current position to init position
		if (vecL.magn > pa.epsValCalc) {		
			myVectorf lnorm = myVectorf._normalize(vecL);//unitlength vector of l
			myVectorf lprime = myVectorf._sub(vecL, _p1.vecLOld);		//lprime - time derivative of length, subtract old length vector from new length vector ?
			double KsTerm = constVal1 * (vecL.magn);//-d);
			double KdTerm = constVal2 * (lprime._dot(lnorm));//was _dot(vecL) ->should be component in direction of normal TODO verify

//			double KsTerm = _p1.kskdVals[0] * (vecL.magn);//-d);
//			double KdTerm = _p1.kskdVals[1] * (lprime._dot(lnorm));//was _dot(vecL) ->should be component in direction of normal TODO verify
			double fp = (KsTerm + KdTerm);
			result = new myVectorf[] {myVectorf._mult(lnorm,-fp),myVectorf._mult(lnorm, fp)};
		} else {//only add force if magnitude of distance vector is not 0
			//if disp is very small, move to original position, return 0 force 
			_p1.aPosition[_p1.curIDX].set(_p1.initPos);
			result = new myVectorf[]{new myVectorf(),new myVectorf()};
		}
		_p1.vecLOld.set(vecL);
		return result;
	}	
	@Override
	public String toString(){return super.toString() + "\tSpring Constant :  " + String.format("%.2f",constVal1) + " \tDamping Constant : "+String.format("%.2f",constVal2) ;}	
}//mySpringToRest

class mySpring{//spring between a particle and a fixed location (the particle's rest position
	public myParticle a;
	public double restLen,  ks, kd;	
	public DoubleMatrix fixedPos, Jp, Jv;//jacobians for position and velocity

	public mySpring(myParticle _a, double _ks, double _kd) {
		a=_a;
		fixedPos=new DoubleMatrix( a.initPos.asDblArray());
		restLen=0;ks=_ks;kd=_kd;
		initJacobians();
	}
	
	public void initJacobians() {
		Jp = DoubleMatrix.eye(3);//overwritten when 
		Jv = DoubleMatrix.eye(3);
		Jv.muli(kd);
	}
	
	

}//class mySpring

abstract class myCollider {
	protected static DancingBalls pa;
	public static int ID_gen = 0;
	public int ID;
	public String name;
	public CollisionType colType;
	
	public myVectorf drawLoc;			//drawn location of this collider - only different from center if needed for display purposes

	public double Krest,			//coefficent of restitution - how much bounce do we have :1 total bounce, 0 no bounce.  multiply this against part's Velperp
				muFrict;         //friction coefficient

	public static final int
		NoCol = 0,					//0 if no collision, 
		BrchCol = 1,				//1 if collision via breach - push back to legal position, address vel and frc concerns
		NextCol = 2,				//2 if collision next timestep  
		CntctCol = 3;				//3 if need to counter force due to contact - within some epsilon of part radius distance from collider surface		
	
	public static final double partRad = .04;
	
	public myCollider(DancingBalls _p, String _n, myVectorf _drawLoc, CollisionType _colType) {
 		pa = _p;
   		ID = ID_gen++;
  		name = new String(_n);
  		drawLoc = _drawLoc;
  		colType = _colType;
	}
	
	//checks if particle location + deltaT partVel will cause a collision, or if particle is in contact without
	//a normal-dir component of velocity, in which case it will modify the force
	//0 vec if no collision, 1 if collision via breach, 2 if collision next timestep, 3 if need to counter force due to contact	
	public abstract int checkCollision(double deltaT, myParticle part);

	//if particle has breached collider somehow, move particle along appropriate normal direction until not breached anymore, and check particle's velocity(and reverse if necessary)
	//1 if collision via breach, 2 if collision next timestep, 3 if need to counter force due to contact
	public abstract void handleCollision(myParticle part, int res);
	
}//myCollider


class sphereCollider extends myCollider{
	public myVectorf center,			//actual location of this collider, w/respect to particles - used for sphere
		radius;						//radius around center for ellipsoid (in each direction for x,y,z)
	
	public double avgRadius;		//average radius, for rouch col calculations
	public double[] minMaxRadius;		//minimum dist from center to still be inside (in case of ellipse, to minimize calculations) min idx 0, max idx 1


	public boolean  intRefl;			//internal reflections? for sphere (collide on inside)
//	myCollider(string _n, const Eigen::Vector3d& _dr, const Eigen::Vector3d& _ctr, const Eigen::Vector3d& _rad, bool _inRefl) :							//sphere collider
//		ID(++ID_gen), name(_n), colType(SPHERE), drawLoc(_dr), center(_ctr), radius(_rad), minMaxRadius(2), planeNormal(), verts(), peq(4), intRefl(_inRefl), Krest(1) {
//		initCollider();
//	}

	public sphereCollider(DancingBalls _p, String _n, myVectorf _drawLoc, myVectorf _ctr, myVectorf _rad, boolean _intRefl) {
		super(_p,  _n, _drawLoc, CollisionType.SPHERE);
		intRefl = _intRefl;
		center = _ctr; radius = _rad;
		avgRadius = (radius.x + radius.y + radius.z) * 1.0/3.0;
		findMinMaxRadius();
	}

	//finds minimum and maximum value of radius for ellipsoid sphere, to speed up calculations of collisions
	public void findMinMaxRadius() {
		minMaxRadius = new double[5];
		minMaxRadius[0] = pa.min3(radius.z, radius.x, radius.y);
		minMaxRadius[1] = pa.max3(radius.z, radius.x, radius.y);
		minMaxRadius[2] = minMaxRadius[0] * minMaxRadius[0];	//sq min rad
		minMaxRadius[3] = minMaxRadius[1] * minMaxRadius[1];	//sq max rad
		minMaxRadius[4] = minMaxRadius[0] - pa.tenPartRads;		//min dist to ignore particle collision - min radius - 10x particle radius
	}	

	@Override
	//0 vec if no collision, 1 if collision via breach, 2 if collision next timestep, 3 if need to counter force due to contact
	public int checkCollision(double deltaT, myParticle part) {
		myVectorf partLocVec = new myVectorf(center,part.aPosition[part.curIDX]), vecToCtr = myVectorf._normalize(partLocVec);			//vector from center to particle position to get dist sphere wall
		//far from plane - no need to check further in collision detection
		double distFromCtr = partLocVec.magn + pa.partRad;
		if (distFromCtr < minMaxRadius[4]){ return NoCol;}						// more than 10x part radii from sphere surface - no collision assumed possible
		double distFromCtrDiff  = distFromCtr - minMaxRadius[1];			//compare to largest dimension of ellipsoid - if still positive, then definite breach
		if (distFromCtrDiff > pa.epsValCalc) { return BrchCol; }						//immediate collision - breached plane by more than eps + snowflakerad

		double spdInRadNormDir = part.aVelocity[part.curIDX]._dot(vecToCtr),				//velocity in direction of particle-from-center vector
				accInRadNormDir = part.aForceAcc[part.curIDX]._dot(vecToCtr)/part.mass;		//acc in direction of particle-from-center vector
	
		if((spdInRadNormDir > 0) && (accInRadNormDir > 0)){ return NoCol;}						//not moving toward plane or accelerating toward plane, so no collision possible this time step
		//by here, within col dist of plane, and moving toward, or tangent to, plane - predict motion
		double velPartInRadNormDir = spdInRadNormDir * deltaT,
				accPartInRadNormDir = .5 * deltaT * deltaT * accInRadNormDir,
				distWillMove = velPartInRadNormDir + accPartInRadNormDir;
		
		myVectorf v2ctrORad = myVectorf._elemDiv(vecToCtr, radius);
		
		double a = v2ctrORad._dot(v2ctrORad), b = v2ctrORad._dot(center), c = center._dot(center) -1, ta = 2*a, discr1 = Math.pow(((b*b) - (2*ta*c)),.5), 
				t1 = (-1*b + discr1)/(ta), t2 = (-1*b - discr1)/(ta);
		//set the t value of the intersection to be the minimum of these two values (which would be the edge closest to the eye/origin of the ray)
		double t = Math.max(t1,t2);
		
		if(distFromCtr + distWillMove > t){//will move further from center than location of intersection of vector from center
			return NextCol;
		}
		return NoCol;

//		//calc t so that normalized partLocVec collides with sphere/ellipsoid wall.  if t > len(partLocVec) then no collision
//		
//		
//		
//		
//		
//		myVector partPos = part.aPosition[part.curIDX],				
//				partLocCtr = new myVector(center,partPos);
//		//check if partloc is very far from wall
//		if(((partLocCtr.magn < .95f * minMaxRadius[0]) && intRefl) //less than 90% of the sphere's minimum radius or 111% of max radius
//			|| ((partLocCtr.magn > 1.056f * minMaxRadius[1]) && !intRefl)){return NoCol;}
//		
//		double hfDelT2 = .5 * deltaT * deltaT;
//		myVector multPartFAcc = myVector._mult(part.aForceAcc[part.curIDX], hfDelT2),
//				partVelPoint = myVector._add(myVector._mult(part.aVelocity[part.curIDX],deltaT), multPartFAcc),
//				partMovePoint = myVector._add(partPos, partVelPoint);	//potential movement point for next turn of movement, to see if next turn of movement will hit wall
//
//		myVector partMvCtr = new myVector(center,partMovePoint);
//			if (((partMvCtr.magn < (minMaxRadius[0] + partRad)) && (!intRefl)) ||					//current location is breach 
//			((partMvCtr.magn > (minMaxRadius[1] - partRad)) && (intRefl))) {
//				if (((partLocCtr.magn < (minMaxRadius[0] + partRad)) && (!intRefl)) ||					//current location is breach 
//					((partLocCtr.magn > (minMaxRadius[1] - partRad)) && (intRefl))) {
//					return BrchCol;
//				}
//				else {
//					return NextCol;
//				}
//			}
//		//find point on surface of sphere inline with center and partlocation
//		myVector sNormPartP = getSphereNormal(partPos);				//normal through current point and center, in direction of collision surface
//		myVector partSpherePnt =  myVector._add(center, myVector._mult(sNormPartP,-snoGlobe.snowGlobRad));			//point on ellipsoid surface colinear with center and particle move point
//		double dist2wall = myVector._sub(partSpherePnt, partPos).magn, distFromWallChk = dist2wall - partVelPoint.magn;
//		if (distFromWallChk > pa.epsValCalc) { return NoCol; }
//		else if (distFromWallChk > -pa.epsValCalc) { return CntctCol; }
//		else { return BrchCol; }
	}//checkCollision		
	
	public myVectorf getSphereNormal(myVectorf _loc) {//get normal at a particular location - no matter where inside or outside of sphere, normal built from this point and center will point in appropriate dir
		//if sphere, normal will be either pointing out or in, colinear with line from center to _loc
		myVectorf normDir = myVectorf._sub(center,_loc);//either point into center if internal reflections or point out of center if not
		double mult = ((intRefl) ? 1 : -1);
		normDir._mult(mult);
		normDir._normalize();
		return normDir;
	}//getNormal
	
	@Override
	//if particle has breached planar collider somehow, move particle along appropriate normal direction until not breached anymore, and check particle's velocity(and reverse if necessary)
	//1 if collision via breach, 2 if collision next timestep, 3 if need to counter force due to contact
	public void handleCollision(myParticle part, int res) {
		myVectorf partPos = part.aPosition[part.curIDX], partVel = part.aVelocity[part.curIDX], sphereNormal = getSphereNormal(partPos);

		if (res == 2) {//if close to intersection with sphere boundary
			myVectorf[] partVelComp = pa.getVecFrame(partVel, sphereNormal);
			//partVelComp[0] *= (-1 * Krest);//reverse direction of normal velocity
			partVel.set(myVectorf._add(myVectorf._mult(partVelComp[0],(-1 * Krest)), partVelComp[1]));//should change dir of velocity, decrease tangent velocity for friction
		}//if about to hit collider

		else if (res == 1) {//1 if collision via breach, 2 if collision next timestep, 3 if need to counter force due to contact
			double distFromBreach = myVectorf._sub(partPos, center).magn - (avgRadius - partRad);
			//cout<<"dist from breach "<<distFromBreach<<endl;
			if (((intRefl) && ((distFromBreach) < 0)) || ((!intRefl) && ((distFromBreach) > 0))) {}//cout<<"breach error, not on wrong side of sphere"<<endl;}
			else {//forcibly move particle to just a bit on the right side of the collider, reverse velocity
				distFromBreach *= (1.1);//move slightly more than breach amount
				myVectorf newPos = myVectorf._add(partPos,myVectorf._mult(sphereNormal,distFromBreach));//move back into sphere
				partPos.set(newPos);
				myVectorf[] partVelComp = pa.getVecFrame(partVel, sphereNormal);
				//partVelComp[0] *= -1;//reverse direction of normal velocity
				partVel.set(myVectorf._mult(myVectorf._add(partVelComp[0],partVelComp[1]),-1));//should change dir of velocity, for sphere zeroing tangent velocity
			}
		}//if 1
		else if (res == 3) //diminish all force and velocity in normal dir 
		{//tangent, get forceAcc and add -(forcecomponent in normal dir)
			myVectorf[] partAccComp = pa.getVecFrame(part.aForceAcc[part.curIDX], sphereNormal);
			partAccComp[0]._mult( -1 * Krest);//reverse direction of normal accel
			part.applyForce(myVectorf._add(partAccComp[0], partAccComp[1]));
		}//tangent
	}//handlePlanarBreach	

}//sphereCollider

class planeCollider extends myCollider{
	//plane
	public myVectorf planeNormal;		//normal of this collider, if flat plane
	public myVectorf[] verts;	//vertices of this object, if flat plane
	public double[] peq;		//plane equation values
	
	public planeCollider(DancingBalls _p, String _n, myVectorf _drawLoc, myVectorf[] _verts) {
		super(_p,  _n, _drawLoc, CollisionType.FLAT);
		verts = _verts;
		buildPlaneNorm();
		findPlaneEQ();
	}
	
	//determines the equation coefficients for a planar collider
	public void findPlaneEQ() {
		//Ax + By + Cz + D = 0
		peq = new double[4];
		peq[0] = planeNormal.x;		//A == norm.X
		peq[1] = planeNormal.y;		//B == norm.y
		peq[2] = planeNormal.z;		//C == norm.z
		peq[3] = -planeNormal._dot(verts[0]);//- ((peq[0] * verts[0].x) + (peq[1] * verts[0].y) + (peq[2] * verts[0].z));		//D
	}

	//build normal of planar object
	public void buildPlaneNorm() {
		myVectorf P0P1 = myVectorf._sub(verts[1], verts[0]);
		myVectorf P1P2 = myVectorf._sub(verts[1], verts[2]);
		//planeNormal = P0P1._cross(P1P2);
		planeNormal = P1P2._cross(P0P1);
		planeNormal._normalized();
	}//buildNorm

	@Override
	//0 if no collision, 
	//1 if collision via breach - push back to legal position, address vel and frc concerns
	//2 if collision next timestep  
	//3 if need to counter force due to contact - within some epsilon of part radius distance from collider surface
	public int checkCollision(double deltaT, myParticle part) {
		myVectorf partLocVec = new myVectorf(verts[0],part.aPosition[part.curIDX]);			//vector from point on plane to particle position to get dist from plane
		//far from plane - no need to check further in collision detection
		double distFromPlane = partLocVec._dot(planeNormal) - pa.partRad; 				//distance edge of particle is from plane
		if (distFromPlane  > pa.tenPartRads){ return NoCol;}									//if further away from plane than 10 * particle radius then no collision this or next cycle
		if (distFromPlane  < -pa.epsValCalc) { return BrchCol; }						      	//immediate collision - breached plane by more than eps*rad
		//dist between epsVal and 10 snoflake rads - possible collision next cycle
		double spdInPlaneNormDir = part.aVelocity[part.curIDX]._dot(planeNormal),					//velocity in direction of plane normal - speed toward plane is negative of this
				accInPlaneNormDir = part.aForceAcc[part.curIDX]._dot(planeNormal)/part.mass;		//acc in dir of plane normal - acc toward plane is negative of this
		if((spdInPlaneNormDir > 0) && (accInPlaneNormDir > 0)){ return NoCol;}						//not touching plane, moving toward plane or accelerating toward plane, so no collision possible this time step
		if (distFromPlane  < pa.epsValCalc) { return CntctCol; }								//contact - address forces
		//by here, within col dist of plane, and moving toward, or tangent to, plane - predict motion
		double hfDelT2 = .5 * deltaT * deltaT,
				velPartInPlaneDir = spdInPlaneNormDir * deltaT,
				accPartInPlaneDir = .5 * deltaT * deltaT * accInPlaneNormDir,
				distWillMove = velPartInPlaneDir + accPartInPlaneDir;		
		if(distFromPlane < distWillMove){//particle is closer to plane than how far it is going to move next cycle - will breach after integration
			return NextCol;
		}
		return NoCol;

	}//checkCollision	
	
	//if particle has breached planar collider somehow, move particle along appropriate normal direction until not breached anymore, and check particle's velocity(and reverse if necessary)
	//1 if collision via breach, 2 if collision next timestep, 3 if need to counter force due to contact
	@Override
	public void handleCollision(myParticle part, int res) {
		myVectorf partPos = part.aPosition[part.curIDX];
		double distFromBreach = myVectorf._sub(partPos, verts[0])._dot(planeNormal);
		myVectorf partVel = part.aVelocity[part.curIDX], partFrc = part.aForceAcc[part.curIDX];		
		
		//1 if collision via breach, 2 if collision next timestep, 3 if need to counter force due to contact
		if ((res == 2) || (partVel._dot(planeNormal) < 0)) {//going to breach next time step, or have breached and velocity is still going down - swap velocity direction
			myVectorf[] partVelComp = pa.getVecFrame(partVel, planeNormal);
			//partVelComp[0] *= (-1 * Krest);//reverse direction of normal velocity
			partVel.set(  myVectorf._add(myVectorf._mult(partVelComp[0],(-1 * Krest)), partVelComp[1]));//should change dir of velocity
			// part.color = myVector(1,0,0);
			if (part.solveType == SolverType.VERLET) { handleVerletCol(part); }//handle reflection/velocity change by swapping old and new positions - need to scale by krest
		}//if breached and velocity going away from wall

		else if (res == 1) {           //immediate breach, swap position
			//p.color = myVector(0, 0, 0);
			if (distFromBreach > 0) {}//cout<<"breach error, not on wrong side of plane"<<endl;}
			else if (part.solveType == SolverType.VERLET) { handleVerletCol(part); }	//handle reflection/velocity change by swapping old and new positions - need to scale by krest			
			else {//forcibly move particle to just a bit on the right side of the collider
				distFromBreach *= -(2.001);
				myVectorf newPos = myVectorf._add(partPos, myVectorf._mult(planeNormal,(distFromBreach + pa.epsValCalc)));   //reflect position up from plane by slightly more than breach amount
				//if(p.getSolveType() == GROUND){cout<<"dist from breach "<<distFromBreach<<" old position: "<<partPos<<" new position : "<<newPos<<endl;}
				//part.position.peekFirst().set( newPos);
				partPos.set( newPos);
				myVectorf[] partAccComp = pa.getVecFrame(partFrc, planeNormal);
				myVectorf frcTanDir = myVectorf._normalize(partAccComp[1]),
						//TODO fix this stuff - friction is not working correctly
						tanForce = myVectorf._mult(frcTanDir,-muFrict * (partAccComp[0]._dot(planeNormal)))
				;
				partFrc.set(partAccComp[0]) ;
				partVel.set(0,0,0);// = myVector(0,0,0);//partVelComp[0];//+partVelComp[1];//should change dir of velocity
			}
		}//if 1

		else if (res == 3) {          //contact
			if (part.solveType == SolverType.VERLET) { handleVerletCol(part); }//handle reflection/velocity change by swapping old and new positions - need to scale by krest
		}

		if ((res == 3) || (res == 2) || (res == 1)) {                             //any contact criteria - swap normal force direction
			myVectorf[] partAccComp = pa.getVecFrame(partFrc, planeNormal);
			partAccComp[0]._mult(-1);//reverse direction of normal acc
			//part.forceAcc.peekFirst().set(myVector._add(partAccComp[0], partAccComp[1]));
			partFrc.set(myVectorf._add(partAccComp[0], partAccComp[1]));
		}//tangent
	}//handlePlanarBreach

	public void handleVerletCol(myParticle p) {
		myVectorf tmpOldPos = p.aPosition[p.curIDX], 
				tmpNewPos = p.aOldPos[p.curIDX];         //swapped already
		myVectorf colPt = myVectorf._mult(myVectorf._add(tmpOldPos, tmpNewPos), .5);
		double krTmp = ((1 - Krest) * .25) + Krest;
		myVectorf tmpOldSub = myVectorf._sub(tmpOldPos, colPt), colNDotVec = myVectorf._mult(planeNormal, colPt._dot(planeNormal)),
				tmpNewSub = myVectorf._sub(tmpNewPos, colPt);
		
		tmpOldPos = myVectorf._mult(myVectorf._add(myVectorf._sub(myVectorf._mult(myVectorf._sub(tmpOldPos,colNDotVec),2), tmpOldSub), colPt),krTmp);
		tmpNewPos = myVectorf._add(myVectorf._sub(myVectorf._mult(myVectorf._sub(tmpOldPos,colNDotVec),2), tmpNewSub),colPt);
		p.aPosition[p.curIDX].set(tmpNewPos);
		p.aOldPos[p.curIDX].set(tmpOldPos);
	}
}//planeCollider


//an object to hold the information about a collision 
class collisionEvent{
	
	
	
}

//
//class boxCollider extends myCollider{
//	//check if inside box of planes bounded by 8 verts - treat like 6 bounding planes 
//	public planeCollider[] box;
//
//	public boxCollider(SnowGlobeWin _p, mySnowGlobeWin _win, String _n, myVectorf _drawLoc, myVector[] _verts) {
//		super(_p, _win, _n, _drawLoc, CollisionType.BOX);
//		box = new planeCollider[6];
//		for(int i =0;  i<3;++i){//0->3, 1->4, 2->5 are parallel planes, idxs 0-3 are one plane, 4-7 are parallel plane
//			//String _n, myVectorf _drawLoc, myVector[] _verts)
//			box[i] = new planeCollider(_p, _win, _n+"Side_"+i+"a",);
//		}
//		// TODO Auto-generated constructor stub
//	}
//
//	@Override
//	public int checkCollision(double deltaT, myParticle part) {
//		// TODO Auto-generated method stub
//		return NoCol;
//	}
//
//	@Override
//	public void handleCollision(myParticle part, int res) {
//		// TODO Auto-generated method stub
//		
//	}
//	
//}//boxCollider
