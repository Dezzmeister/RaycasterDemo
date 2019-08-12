package com.dezzy.raycasterdemo;

import java.util.ArrayList;
import java.util.List;
import com.dezzy.raycasterdemo.Raycaster.Point;
import com.dezzy.raycasterdemo.Raycaster.Line;

public class Fizix {

    public static Point moveInWorld(final Point startAt, final Point vel, final List<Line> lines, final float radius, final float spacing, final float iterations) {
        if (iterations == 0) {
            return startAt;
        }

        final List<CollisionPacket> collisions = getFutureCollisions(startAt, vel, lines, radius);

        if (collisions.isEmpty()) {
            return new Point(startAt.x + vel.x, startAt.y + vel.y);
        }
        if (collisions.size() == 1) {
            final CollisionPacket packet = collisions.get(0);
            final Point attemptedDest = new Point(startAt.x + vel.x, startAt.y + vel.y);
            final Point newLocation = approach(attemptedDest, packet, radius, spacing);
            final Point newVelocity = getSlidingVector(vel, packet.wouldHit);

            return moveInWorld(newLocation, newVelocity, lines, radius, spacing, iterations - 1);
        }
        if (collisions.size() == 2) {
            final CollisionPacket packet = determineCloserPacket(startAt, collisions.get(0), collisions.get(1));

            if (packet != null) {
                final Point attemptedDest = new Point(startAt.x + vel.x, startAt.y + vel.y);
                final Point newLocation = approach(attemptedDest, packet, radius, spacing);
                final Point newVelocity = getSlidingVector(vel, packet.wouldHit);

                return moveInWorld(newLocation, newVelocity, lines, radius, spacing, iterations - 1);
            }
        }

        return startAt;
    }

    private static CollisionPacket determineCloserPacket(final Point startAt, final CollisionPacket packet0, final CollisionPacket packet1) {
        final Point segment0Hit = Raycaster.rayHitSegment(startAt, packet1.wouldHitAt, packet0.wouldHit);
        final Point segment1Hit = Raycaster.rayHitSegment(startAt, packet0.wouldHitAt, packet1.wouldHit);

        if (segment0Hit == null && segment1Hit == null) {
            return null;
        }

        return packet0.distanceSqr < packet1.distanceSqr ? packet0 : packet1;
    }

    private static Point approach(final Point attemptedDest, final CollisionPacket approachTo, final float radius, final float spacing) {
        final Point wouldHitAt = approachTo.wouldHitAt;
        final Point offsetVector = new Point(attemptedDest.x - wouldHitAt.x, attemptedDest.y - wouldHitAt.y);
        final float offsetLen = Raycaster.distance(attemptedDest, wouldHitAt);
        offsetVector.x /= offsetLen;
        offsetVector.y /= offsetLen;

        return new Point(wouldHitAt.x + (offsetVector.x * (radius + spacing)), wouldHitAt.y + (offsetVector.y * (radius + spacing)));
    }

    private static Point getSlidingVector(final Point vel, final Line line) {
        final float dot = vel.dot(line.normal);
        final Point p = new Point(line.normal.x * dot, line.normal.y * dot);

        return new Point(vel.x - p.x, vel.y - p.y);
    }

    private static List<CollisionPacket> getFutureCollisions(final Point startAt, final Point vel, final List<Line> lines, float radius) {
        List<CollisionPacket> out = new ArrayList<CollisionPacket>();

        final Point dest = new Point(startAt.x + vel.x, startAt.y + vel.y);
        final float radiusSqr = radius * radius;

        for (int i = 0; i < lines.size(); i++) {
            final Line line = lines.get(i);
            final Point closestPoint = lineHitPoint(line, dest);
            float dsqr = Raycaster.distSqr(closestPoint, dest);

            if (dsqr < radiusSqr) {
                out.add(new CollisionPacket(line, closestPoint, dsqr));
            }
        }

        return out;
    }

    private static Point lineHitPoint(final Line wall, final Point pos) {
        float dot = (((pos.x - wall.p0.x) * (wall.xDiff)) + ((pos.y - wall.p0.y) * (wall.yDiff))) / wall.lensqr;

        float x = wall.p0.x + (dot * wall.xDiff);
        float y = wall.p0.y + (dot * wall.yDiff);

        Point out = new Point(x, y);
        float d0 = Raycaster.distance(out, wall.p0);
        float d1 = Raycaster.distance(out, wall.p1);

        if (d0 + d1 > wall.length + 0.0005f) {
            return (d0 < d1) ? new Point(wall.p0.x, wall.p0.y) : new Point(wall.p1.x, wall.p1.y);
        }

        return out;
    }

    private static class CollisionPacket {
        private final Raycaster.Line wouldHit;
        private final Raycaster.Point wouldHitAt;
        private final float distanceSqr;

        public CollisionPacket(final Raycaster.Line _wouldHit, final Raycaster.Point _wouldHitAt, float _distanceSqr) {
            wouldHit = _wouldHit;
            wouldHitAt = _wouldHitAt;
            distanceSqr = _distanceSqr;
        }
    }
}
