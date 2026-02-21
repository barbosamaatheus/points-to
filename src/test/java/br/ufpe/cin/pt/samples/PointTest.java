package br.ufpe.cin.pt.samples;

import static org.junit.Assert.assertEquals;

import org.junit.Ignore;

public class PointTest {
  @Ignore
  public void testPoint() {
    Point point = new Point(1, 2);
    assertEquals(1.0, point.getX(), 0.000001);
    assertEquals(2.0, point.getY(), 0.000001);
  }

  @Ignore
  public void testPoints() {
    Point point1 = new Point(1, 2);
    Point point2 = new Point(3, 4);
    Point point3 = point2;
    System.out.println(point3.getX() + point3.getY());
    assertEquals(5.0, point1.distance(point2), 0.000001);
    assertEquals(5.0, point3.distance(point2), 0.000001);
  }

}

