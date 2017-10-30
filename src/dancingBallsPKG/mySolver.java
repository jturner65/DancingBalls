package dancingBallsPKG;

public class mySolver {
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

