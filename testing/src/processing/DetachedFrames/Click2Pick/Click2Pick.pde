/**
 * Frame API.
 * by Jean Pierre Charalambos.
 * 
 * This example illustrates the powerful Frame API used to convert points and
 * vectors along a node hierarchy. The following node hierarchy is implemented:
 * 
 * world
 * ^
 * |\
 * | \
 * f1 eye
 * ^   ^
 * |\   \
 * | \   \
 * f2 f3  f5
 * ^
 * |
 * |
 * f4
 *
 * Note that the hierarchy is implemented using detached-node specializations
 * and hence it gets manually traversed. Check the draw() method and contrast it
 * with the one implemented by the Click2Pick attached-node example version.
 *
 * Press the space bar to browse the different conversion methods shown here.
 */

import frames.primitives.*;
import frames.core.*;
import frames.processing.*;

Scene scene;
InteractiveFrame f1, f2, f3, f4, f5;
Vector pnt = new Vector(40, 30, 20);
Vector vec = new Vector(50, 50, 50);
PFont font16, font13;
Mode mode;
int wColor = color(255, 255, 255);
int f1Color = color(255, 0, 0);
int f2Color = color(0, 255, 0);
int f3Color = color(0, 0, 255);
int f4Color = color(255, 0, 255);
int f5Color = color(255, 255, 0);

enum Mode {
  m1, m2, m3, m4, m5, m6
}

//Choose FX2D, JAVA2D, P2D or P3D
String renderer = P3D;

void setup() {
  size(900, 900, renderer);
  scene = new Scene(this);
  mode = Mode.m1;

  scene.setRadius(200);
  scene.fit(1);

  f1 = new InteractiveFrame(f1Color);
  f1.translate(-50, -20, 30);
  f1.scale(1.3f);

  f2 = new InteractiveFrame(f1, f2Color);
  f2.translate(60, -40, -30);
  f2.scale(1.2f);

  f3 = new InteractiveFrame(f1, f3Color);
  f3.translate(60, 55, -30);
  f3.rotate(new Quaternion(new Vector(0, 1, 0), -HALF_PI));
  f3.scale(1.1f);

  f4 = new InteractiveFrame(f2, f4Color);
  f4.translate(60, -55, 30);
  f4.rotate(new Quaternion(new Vector(0, 1, 0), QUARTER_PI));
  f4.scale(0.9f);

  f5 = new InteractiveFrame(scene.eye(), f5Color);
  f5.translate(-100, 0, -250);

  font16 = loadFont("FreeSans-16.vlw");
  font13 = loadFont("FreeSans-13.vlw");
}

void draw() {
  background(0);

  //world:
  scene.drawAxes();
  pushStyle();
  stroke(wColor);
  strokeWeight(10);
  point(pnt.x(), pnt.y(), pnt.z());
  popStyle();

  pushMatrix();
  scene.applyTransformation(f1);
  f1.draw(scene);
  pushMatrix();
  scene.applyTransformation(f3);
  f3.draw(scene);
  popMatrix();
  pushMatrix();
  scene.applyTransformation(f2);
  f2.draw(scene);
  pushMatrix();
  scene.applyTransformation(f4);
  f4.draw(scene);
  popMatrix();
  popMatrix();
  popMatrix();

  //eye
  pushMatrix();
  scene.applyTransformation(scene.eye());
  pushMatrix();
  scene.applyTransformation(f5);
  f5.draw(scene);
  popMatrix();
  popMatrix();

  drawMode();
  displayText();
}

void drawMode() {
  // points
  pushStyle();
  noStroke();
  fill(0, 255, 255);
  switch (mode) {
  case m1: // f2 -> world
    drawArrowConnectingPoints(f2.worldLocation(pnt));
    break;
  case m2: // f2 -> f1
    drawArrowConnectingPoints(f1, f1.location(pnt, f2));
    break;
  case m3: // f1 -> f2
    drawArrowConnectingPoints(f2, f2.location(pnt, f1));
    break;
  case m4: // f3 -> f4
    drawArrowConnectingPoints(f4, f4.location(pnt, f3));
    break;
  case m5: // f4 -> f3
    drawArrowConnectingPoints(f3, f3.location(pnt, f4));
    break;
  case m6: // f5 -> f4
    drawArrowConnectingPoints(f4, f4.location(pnt, f5));
    break;
  }
  popStyle();

  // vectors
  pushStyle();
  noStroke();
  fill(125);
  switch (mode) {
  case m1: // f2 -> world
    drawVector(f2, vec);
    drawVector(f2.worldDisplacement(vec));
    break;
  case m2: // f2 -> f1
    drawVector(f2, vec);
    drawVector(f1, f1.displacement(vec, f2));
    break;
  case m3: // f1 -> f2
    drawVector(f1, vec);
    drawVector(f2, f2.displacement(vec, f1));
    break;
  case m4: // f3 -> f4
    drawVector(f3, vec);
    drawVector(f4, f4.displacement(vec, f3));
    break;
  case m5: // f4 -> f3
    drawVector(f4, vec);
    drawVector(f3, f3.displacement(vec, f4));
    break;
  case m6: // f5 -> f4
    drawVector(f5, vec);
    drawVector(f4, f4.displacement(vec, f5));
    break;
  }
  popStyle();
}

void displayText() {
  pushStyle();
  Vector pos;
  scene.beginScreenDrawing();
  textFont(font13);
  fill(f1Color);
  pos = scene.screenLocation(f1.position());
  text("Frame 1", pos.x(), pos.y());
  fill(f2Color);
  pos = scene.screenLocation(f2.position());
  text("Frame 2", pos.x(), pos.y());
  fill(f3Color);
  pos = scene.screenLocation(f3.position());
  text("Frame 3", pos.x(), pos.y());
  fill(f4Color);
  pos = scene.screenLocation(f4.position());
  text("Frame 4", pos.x(), pos.y());
  fill(f5Color);
  pos = scene.screenLocation(f5.position());
  text("Frame 5", pos.x(), pos.y());
  fill(wColor);
  textFont(font16);
  text("Press the space bar to change mode", 5, 15);
  switch (mode) {
  case m1: // f2 -> world
    text("Converts vectors (grey arrows) and points (see the cyan arrow) from node 2 to world", 5, 35);
    break;
  case m2: // f2 -> f1
    text("Converts vectors (grey arrows) and points (see the cyan arrow) from node 2 to node 1", 5, 35);
    break;
  case m3: // f1 -> f2
    text("Converts vectors (grey arrows) and points (see the cyan arrow) from node 1 to node 2", 5, 35);
    break;
  case m4: // f3 -> f4
    text("Converts vectors (grey arrows) and points (see the cyan arrow) from node 3 to node 4", 5, 35);
    break;
  case m5: // f4 -> f3
    text("Converts vectors (grey arrows) and points (see the cyan arrow) from node 4 to node 3", 5, 35);
    break;
  case m6: // f5 -> f4
    text("Converts vectors (grey arrows) and points (see the cyan arrow) from node 5 to node 4", 5, 35);
    break;
  }
  scene.endScreenDrawing();
  popStyle();
}

void drawArrowConnectingPoints(Vector to) {
  drawArrow(null, pnt, to);
}

void drawArrowConnectingPoints(Frame node, Vector to) {
  drawArrow(node, pnt, to);
}

void drawVector(Vector to) {
  drawArrow(null, new Vector(), to);
}

void drawVector(Frame node, Vector to) {
  drawArrow(node, new Vector(), to);
}

void drawArrow(Frame node, Vector from, Vector to) {
  if (node != null) {
    pushMatrix();
    //scene.applyModelView(node.worldMatrix());// world, is handy but inefficient
    scene.applyWorldTransformation(node);
    scene.drawArrow(from, to, 1);
    popMatrix();
  } else
    scene.drawArrow(from, to, 1);
}

void keyPressed() {
  if (key == ' ')
    switch (mode) {
    case m1:
      mode = Mode.m2;
      break;
    case m2:
      mode = Mode.m3;
      break;
    case m3:
      mode = Mode.m4;
      break;
    case m4:
      mode = Mode.m5;
      break;
    case m5:
      mode = Mode.m6;
      break;
    case m6:
      mode = Mode.m1;
      break;
    }
  if (key == 'v' || key == 'V')
    scene.flip();
  if (key == '+')
    scene.eye().setScaling(scene.eye().scaling() * 1.1f);
  if (key == '-')
    scene.eye().setScaling(scene.eye().scaling() / 1.1f);
  if (key == 'e')
    f1.enableTracking(!f1.isTrackingEnabled());
}

void mouseMoved(MouseEvent event) {
  if (event.isControlDown())
    scene.lookAround();
  else if (event.isShiftDown())
    scene.translate();
  else
    scene.spin();
}

void mouseWheel(MouseEvent event) {
  scene.scale(event.getCount() * 20);
}

void mouseClicked(MouseEvent event) {
  if (event.getCount() == 1) {
    scene.track(new Frame[]{f1, f2, f3, f4, f5});
  }
  if (event.getCount() == 2)
    if (event.getButton() == LEFT)
      scene.focus();
    else
      scene.align();
}
