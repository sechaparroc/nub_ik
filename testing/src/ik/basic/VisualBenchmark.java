package ik.basic;

import frames.core.Frame;
import frames.core.Graph;
import frames.core.constraint.BallAndSocket;
import frames.core.constraint.PlanarPolygon;
import frames.ik.CCDSolver;
import frames.ik.ChainSolver;
import frames.ik.HAEASolver;
import frames.ik.evolution.GASolver;
import frames.ik.evolution.HillClimbingSolver;
import frames.ik.Solver;
import frames.ik.jacobian.PseudoInverseSolver;
import frames.ik.jacobian.TransposeSolver;
import frames.primitives.Quaternion;
import frames.primitives.Vector;
import frames.processing.Scene;
import frames.processing.Shape;
import ik.common.Joint;
import processing.core.PApplet;
import processing.core.PShape;
import processing.event.MouseEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Created by sebchaparr on 8/10/18.
 */
public class VisualBenchmark extends PApplet {
    //TODO : Update
    int num_joints = 10;
    float targetRadius = 12;
    float boneLength = 50;

    Random random = new Random();

    Scene scene;
    //Methods
    int num_solvers = 10;
    ArrayList<Solver> solvers;
    ArrayList<ArrayList<Frame>> structures = new ArrayList<>();
    ArrayList<Shape> targets = new ArrayList<Shape>();

    public void settings() {
        size(700, 700, P3D);
    }

    public void setup() {
        scene = new Scene(this);
        scene.setType(Graph.Type.ORTHOGRAPHIC);
        scene.setRadius(num_joints * boneLength / 1.5f);
        scene.fitBallInterpolation();

        PShape redBall = createShape(SPHERE, targetRadius);
        redBall.setStroke(false);
        redBall.setFill(color(255,0,0));

        for(int i = 0; i < num_solvers; i++) {
            targets.add(new Shape(scene, redBall));
        }

        float down = PI/2;
        float up = PI/2;
        float left = PI/2;
        float right = PI/2;

        for(int i = 0; i < num_solvers; i++){
            structures.add(generateChain(num_joints, boneLength, new Vector(i*2*scene.radius()*0.8f/num_solvers - 0.8f*0.8f*scene.radius(), 0, 0)));
        }

        ArrayList<Vector> vertices = new ArrayList<Vector>();
        float v = 20;
        float w = 20;

        vertices.add(new Vector(-w, -v));
        vertices.add(new Vector(w, -v));
        vertices.add(new Vector(w, v));
        vertices.add(new Vector(-w, v));

        for (int i = 0; i < num_joints - 1; i++) {
            PlanarPolygon constraint = new PlanarPolygon(vertices);
            constraint.setHeight(boneLength / 2.f);
            Vector twist = structures.get(0).get(i + 1).translation().get();
            Quaternion offset = new Quaternion(new Vector(0, 1, 0), radians(40));
            offset = new Quaternion();
            Quaternion rest = Quaternion.compose(structures.get(0).get(i).rotation().get(), offset);
            constraint.setRestRotation(rest, new Vector(0, 1, 0), twist);
            constraint.setAngle(PI/3f);
            for(ArrayList<Frame> structure : structures){
                //structure.get(i).setConstraint(constraint);
            }
        }

        /*
        for (int i = 1; i < num_joints - 1; i++) {
            Vector twist = structures.get(0).get(i + 1).translation().get();
            BallAndSocket constraint = new BallAndSocket(down, up, left, right);
            constraint.setRestRotation(structures.get(0).get(i).rotation().get(), new Vector(0, 1, 0), twist);
            for(ArrayList<Frame> structure : structures){
                //structure.get(i).setConstraint(constraint);
            }
        }
        */

        scene.eye().rotate(new Quaternion(new Vector(1,0,0), PI/2.f));
        scene.eye().rotate(new Quaternion(new Vector(0,1,0), PI));

        solvers = new ArrayList<>();

        solvers.add(new HillClimbingSolver(radians(3), structures.get(0)));
        solvers.add(new HillClimbingSolver(5, radians(3), structures.get(1)));
        solvers.add(new HillClimbingSolver(radians(5), structures.get(2)));
        solvers.add(new HillClimbingSolver(5, radians(5), structures.get(3)));
        solvers.add(new ChainSolver(structures.get(4)));
        solvers.add(new GASolver(structures.get(5), 10));
        solvers.add(new HAEASolver(structures.get(6), 10, true));
        solvers.add(new HAEASolver(structures.get(7), 10, false));
        solvers.add(new TransposeSolver(structures.get(8)));
        solvers.add(new PseudoInverseSolver(structures.get(9)));
        //solvers.add(new CCDSolver(structures.get(2)));

        for(int i = 0; i < num_solvers; i++){
            solvers.get(i).error = 0.5f;
            solvers.get(i).timesPerFrame = 1;
            solvers.get(i).maxIter = 300;
            if(i != 0)targets.get(i).setReference(targets.get(0));
            if(solvers.get(i) instanceof HillClimbingSolver) {
                ((HillClimbingSolver) solvers.get(i)).setTarget(targets.get(i));
                targets.get(i).setPosition( ((HillClimbingSolver) solvers.get(i)).endEffector().position());
            }
            if(solvers.get(i) instanceof ChainSolver) {
                ((ChainSolver) solvers.get(i)).setTarget(targets.get(i));
                targets.get(i).setPosition( ((ChainSolver) solvers.get(i)).endEffector().position());
            }
            if(solvers.get(i) instanceof CCDSolver) {
                ((CCDSolver) solvers.get(i)).setTarget(targets.get(i));
                targets.get(i).setPosition( ((CCDSolver) solvers.get(i)).endEffector().position());
            }
            if(solvers.get(i) instanceof GASolver) {
                GASolver solver = (GASolver) solvers.get(i);
                solver.setTarget(solver.endEffector(), targets.get(i));
                targets.get(i).setPosition(solver.endEffector().position());
            }
            if(solvers.get(i) instanceof HAEASolver) {
                HAEASolver solver = (HAEASolver) solvers.get(i);
                solver.setTarget(solver.endEffector(), targets.get(i));
                targets.get(i).setPosition(solver.endEffector().position());
            }
            if(solvers.get(i) instanceof TransposeSolver) {
                TransposeSolver solver = (TransposeSolver) solvers.get(i);
                solver.setTarget(targets.get(i));
                targets.get(i).setPosition(solver.endEffector().position());
            }
            if(solvers.get(i) instanceof PseudoInverseSolver) {
                PseudoInverseSolver solver = (PseudoInverseSolver) solvers.get(i);
                solver.setTarget(targets.get(i));
                targets.get(i).setPosition(solver.endEffector().position());
            }
        }
    }

    public void draw() {
        background(0);
        lights();
        //Draw Constraints
        scene.drawAxes();
        if(solve) {
            for(Solver solver : solvers){
                if(solver instanceof TransposeSolver)
                    solver.solve();
            }
        }
        scene.traverse();
        scene.beginHUD();
        for(Solver solver : solvers) {
            fill(255);
            textSize(12);
            if (solver instanceof HillClimbingSolver) {
                HillClimbingSolver s = (HillClimbingSolver) solver;
                Frame f = s.chain().get(0);
                Vector pos = scene.screenLocation(f.position());
                if(s.powerLaw()){
                    text("Power Law  \n Sigma: " + String.format( "%.2f", s.sigma()) + "\n Alpha: " + String.format( "%.2f", s.alpha()) + "\n Error: " + String.format( "%.2f", s.distanceToTarget()), pos.x() - 30, pos.y() + 10, pos.x() + 30, pos.y() + 50);
                } else{
                    text("Gaussian  \n Sigma: " + String.format( "%.2f", s.sigma()) + "\n Error: " + String.format( "%.2f", s.distanceToTarget()), pos.x() - 30, pos.y() + 10, pos.x() + 30, pos.y() + 50);
                }
            } else if (solver instanceof ChainSolver) {
                Frame f = ((ChainSolver)solver).chain().get(0);
                Vector pos = scene.screenLocation(f.position());
                text("FABRIK", pos.x() - 30, pos.y() + 10, pos.x() + 30, pos.y() + 50);
            } else if(solver instanceof  GASolver){
                Frame f = ((GASolver)solver).structure().get(0);
                Vector pos = scene.screenLocation(f.position());
                text("Genetic \n Algorithm" + "\n Error: " + String.format( "%.2f", ((GASolver)solver).best()), pos.x() - 30, pos.y() + 10, pos.x() + 30, pos.y() + 50);
            } else if(solver instanceof  HAEASolver){
                Frame f = ((HAEASolver)solver).structure().get(0);
                Vector pos = scene.screenLocation(f.position());
                text("HAEA \n Algorithm" + "\n Error: " + String.format( "%.2f", ((HAEASolver)solver).best()), pos.x() - 30, pos.y() + 10, pos.x() + 30, pos.y() + 50);
            } else if(solver instanceof  TransposeSolver){
                Frame f = ((TransposeSolver)solver).chain().get(0);
                Vector pos = scene.screenLocation(f.position());
                text("Transpose", pos.x() - 30, pos.y() + 10, pos.x() + 30, pos.y() + 50);
            } else if(solver instanceof  PseudoInverseSolver){
                Frame f = ((PseudoInverseSolver)solver).chain().get(0);
                Vector pos = scene.screenLocation(f.position());
                text("PseudoInverseSolver", pos.x() - 30, pos.y() + 10, pos.x() + 30, pos.y() + 50);
            }
        }
        scene.endHUD();
    }

    public void setConstraint(float down, float up, float left, float right, Frame f, Vector twist, float boneLength){
        BallAndSocket constraint = new BallAndSocket(down, up, left, right);
        constraint.setRestRotation(f.rotation().get(), f.displacement(new Vector(0, 1, 0)), f.displacement(twist));
        f.setConstraint(constraint);
    }

    public ArrayList<Frame> generateChain(int num_joints, float boneLength, Vector translation) {
        Joint prevJoint = null;
        Joint chainRoot = null;
        for (int i = 0; i < num_joints; i++) {
            Joint joint;
            joint = new Joint(scene);
            if (i == 0)
                chainRoot = joint;
            if (prevJoint != null) joint.setReference(prevJoint);
            float x = 0;
            float z = 1;
            float y = 0;
            Vector translate = new Vector(x,y,z);
            translate.normalize();
            translate.multiply(boneLength);
            joint.setTranslation(translate);
            joint.setPrecision(Frame.Precision.FIXED);
            prevJoint = joint;
        }
        //Consider Standard Form: Parent Z Axis is Pointing at its Child
        chainRoot.setTranslation(translation);
        //chainRoot.setupHierarchy();
        chainRoot.setRoot(true);
        return (ArrayList) scene.branch(chainRoot);
    }

    public Frame generateRandomReachablePosition(List<? extends Frame> original){
        ArrayList<? extends Frame> chain = copy(original);
        for(int i = 0; i < chain.size(); i++){
            chain.get(i).rotate(new Quaternion(Vector.random(), (float)(random.nextGaussian()*random.nextFloat()*PI/2)));
        }
        return chain.get(chain.size()-1);
    }

    public ArrayList<Frame> copy(List<? extends Frame> chain) {
        ArrayList<Frame> copy = new ArrayList<Frame>();
        Frame reference = chain.get(0).reference();
        if (reference != null) {
            reference = new Frame(reference.position().get(), reference.orientation().get());
        }
        for (Frame joint : chain) {
            Frame newJoint = new Frame();
            newJoint.setReference(reference);
            newJoint.setPosition(joint.position().get());
            newJoint.setOrientation(joint.orientation().get());
            newJoint.setConstraint(joint.constraint());
            copy.add(newJoint);
            reference = newJoint;
        }
        return copy;
    }


    boolean solve = false;
    public void keyPressed(){
        if(key == 'w' || key == 'W'){
            solve = !solve;
        }
        if(key == 's' || key == 'S'){
            Frame f = generateRandomReachablePosition(structures.get(0));
            targets.get(0).setPosition(f.position());
        }
        if(key == 'd' || key == 'D'){
            for(List<Frame> structure : structures) {
                for (Frame f : structure) {
                    f.setRotation(new Quaternion());
                }
            }
        }

        // /* Uncomment this to debug a Specific Solver
        if(key == 'z' || key == 'Z'){
            solvers.get(solvers.size()-1).solve();
        }
        // /*
    }

    @Override
    public void mouseMoved() {
        scene.cast();
    }

    public void mouseDragged() {
        if (mouseButton == LEFT){
            scene.spin();
        } else if (mouseButton == RIGHT) {
            scene.translate();
        } else {
            scene.scale(scene.mouseDX());
        }
    }

    public void mouseWheel(MouseEvent event) {
        scene.scale(event.getCount() * 20);
    }

    public void mouseClicked(MouseEvent event) {
        if (event.getCount() == 2)
            if (event.getButton() == LEFT)
                scene.focus();
            else
                scene.align();
    }


    public static void main(String args[]) {
        PApplet.main(new String[]{"ik.basic.VisualBenchmark"});
    }
}
