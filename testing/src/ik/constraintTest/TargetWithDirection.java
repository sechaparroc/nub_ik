package ik.constraintTest;

import frames.core.Frame;
import frames.core.Graph;
import frames.ik.ChainSolver;
import frames.primitives.Quaternion;
import frames.primitives.Vector;
import frames.processing.Scene;
import frames.timing.TimingTask;
import ik.common.Arrow;
import ik.common.Joint;
import processing.core.PApplet;
import processing.core.PShape;
import processing.event.MouseEvent;
import java.util.ArrayList;

public class TargetWithDirection  extends PApplet {
    float radius = 10;
    Frame target;

    Scene scene;
    ArrayList<Joint> joints = new ArrayList<>();
    int numJoints = 4;
    ChainSolver solver;

    public void settings() {
        size(700, 700, P3D);
    }

    public void setup() {
        scene = new Scene(this);
        scene.setType(Graph.Type.ORTHOGRAPHIC);
        scene.setRadius(200);
        scene.fit(1);
        scene.setRightHanded();

        PShape redBall = createShape(SPHERE, radius);
        redBall.setStroke(false);
        redBall.setFill(color(255,0,0));

        target = new Frame(scene, redBall);
        target.setPickingThreshold(0);

        //Generate a basic structure
        for(int i = 0 ; i < numJoints; i++){
            Joint j = new Joint(scene,radius);
            if(i != 0) {
                j.setReference(joints.get(joints.size() - 1));
                j.translate(50, 0, 0);
            } else{
                j.setRoot(true);
            }
            joints.add(j);
        }
        //Define arrow translation based on structure
        target.setPosition(joints.get(numJoints - 1).position());

        Arrow arrow = new Arrow(scene, target, new Vector(50,0,0),  color(255,0,0));
        scene.eye().rotate(new Quaternion(new Vector(1,0,0), PI/2.f));
        scene.eye().rotate(new Quaternion(new Vector(0,1,0), PI));

        solver = new ChainSolver((ArrayList<? extends Frame>) scene.branch(joints.get(0)));
        solver.timesPerFrame = 1;
        solver.setTargetDirection(new Vector(1,0,0));
        solver.setTarget(target);
        TimingTask task = new TimingTask() {
            @Override
            public void execute() {
                if(solve) solver.solve();
            }
        };
        scene.registerTask(task);
        task.run(40);
    }

    public void draw() {
        background(0);
        lights();
        scene.drawAxes();
        scene.render();
        pushMatrix();
        scene.applyWorldTransformation(target);
        scene.drawAxes(50);
        popMatrix();
        pushStyle();
        popStyle();
    }

    Frame n = null;
    float d = 5;
    boolean solve = false;
    public void keyPressed(){
        if(key == 'w'){
            solve = !solve;
        }
        if(key == 'q'){
            solver.solve();
        }
    }

    @Override
    public void mouseMoved() {
        scene.cast();
    }

    public void mouseDragged() {
        if (mouseButton == LEFT){
            scene.spin();
        } else if (mouseButton == RIGHT) {
            if(scene.trackedFrame() instanceof Arrow){
                Vector vector = new Vector(scene.mouse().x(), scene.mouse().y());
                ((Arrow) scene.trackedFrame()).applyReferenceRotation(vector);
            }else {
                scene.translate();
            }
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
        PApplet.main(new String[]{"ik.constraintTest.TargetWithDirection"});
    }

}


