package com.dezzy.raycasterdemo;

import android.view.View;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import android.graphics.*;
import android.content.*;
import android.view.*;
import android.os.*;
import java.util.concurrent.*;

public class Raycaster extends View
{
    private static final String TAG = "Raycaster";

    private static final float FOG_LIMIT = 8;

    private final Paint paint = new Paint();

    private final List<Line> lines = new ArrayList<Line>();
    private Point pos = new Point(0, 0);
    private Point dir = new Point(0, 1);
    private Point sidedir = new Point(1, 0);
    /**
     * Actually is half the screen
     **/
    private Point screen = new Point(0.45f, 0);

    private float[] zbuf;
    private int width;
    private int height;
    private int biggerDim;
    private int renderWidth;
    private int renderHeight;

    private float[] wallDistLUT;

    private Bitmap target;
    private int[] pixels;

    private static final int THREADS = 8;
    private final ThreadRenderer[] renderers = new ThreadRenderer[THREADS];
    private final LatchRef latchRef = new LatchRef(new CountDownLatch(THREADS));
    private final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(THREADS);

    private final String root = Environment.getExternalStorageDirectory().getAbsolutePath() + "/raycaster-demo-files/";

    private Bitmap floorTexture;
    private Bitmap ceilTexture;

    private final CircleControl moveControl = new MoveControl(new Point(0.19f, 0.8f), 0.065f, 0.12f);
    private final CircleControl lookControl = new LookControl(new Point(0.81f, 0.8f), 0.065f, 0.12f);

    private volatile boolean rendering = false;

    private static final float PLAYER_RADIUS = 0.25f;
    private static final float SPACING = 0.015f;

    public Raycaster(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        paint.setColor(Color.GREEN);
        paint.setAntiAlias(true);
        paint.setStrokeWidth(1);

        loadMap(root + "map.txt");
        System.out.println(root);
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!rendering) {
                    if (moveControl.grabbed) {
                        moveControl.handleMovement();
                        invalidate();
                    }

                    if (lookControl.grabbed) {
                        lookControl.handleMovement();
                        invalidate();
                    }
                    rendering = true;
                }
            }
        }, 0, 20);
    }

    private void loadMap(final String path) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(new File(path)));

            Map<String, Bitmap> textures = new HashMap<>();

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim().replace(" ","");

                if (line.indexOf("texture") == 0) {
                    String name = line.substring(7, line.indexOf("="));
                    String fileName = line.substring(line.indexOf("=") + 1);
                    textures.put(name, BitmapFactory.decodeFile(root + fileName));
                } else if (line.indexOf("line") == 0) {
                    String[] fields = line.substring(4).split(",");
                    float x0 = Float.parseFloat(fields[0]);
                    float y0 = Float.parseFloat(fields[1]);
                    float x1 = Float.parseFloat(fields[2]);
                    float y1 = Float.parseFloat(fields[3]);
                    int xTiles = 1;
                    int yTiles = 1;

                    if (fields.length >= 7) {
                        xTiles = Integer.parseInt(fields[4]);
                        yTiles = Integer.parseInt(fields[5]);
                    }
                    lines.add(new Line(new Point(x0, y0), new Point(x1, y1), textures.get((fields.length >= 7) ? fields[6] : fields[4]), xTiles, yTiles));
                } else if (line.indexOf("floortex") == 0) {
                    floorTexture = textures.get(line.substring(8));
                } else if (line.indexOf("ceiltex") == 0) {
                    ceilTexture = textures.get(line.substring(7));
                } else if (line.indexOf("startpos") == 0) {
                    String[] fields = line.substring(8).split(",");
                    float x = Float.parseFloat(fields[0]);
                    float y = Float.parseFloat(fields[1]);
                    pos.x = x;
                    pos.y = y;
                }
            }

            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveDimensions() {
        width = getWidth();
        height = getHeight();
        biggerDim = Math.max(width, height);

        if (height/(float)width > 1.0f) {
            renderWidth = 200;
            renderHeight = (int)((height/(float)width) * renderWidth);
        } else {
            renderWidth = 200;
            renderHeight = (int)((height/(float)width) * renderWidth);
            screen.x = 0.95f;
        }

        zbuf = new float[renderWidth];
        populateWallDistLUT();

        target = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888);
        pixels = new int[renderWidth * renderHeight];

        int interval;
        if (renderWidth % THREADS == 0) {
            interval = renderWidth/THREADS;
        } else {
            interval = (renderWidth - (renderWidth % THREADS))/THREADS;
        }

        for (int i = 0; i < THREADS; i++) {
            int startX = i * interval;
            int endX = (i + 1) * interval;

            if (i == THREADS - 1) {
                endX = renderWidth;
            }
            renderers[i] = new ThreadRenderer(pixels, zbuf, wallDistLUT, floorTexture, ceilTexture, pos, dir, screen, renderWidth, renderHeight, lines, startX, endX, latchRef);
            executor.execute(renderers[i]);
        }
    }

    private void populateWallDistLUT() {
        wallDistLUT = new float[renderHeight/2];
        wallDistLUT[0] = Float.POSITIVE_INFINITY;
        for (int i = 1; i < renderHeight/2; i++) {
            int y = i + (renderHeight/2);
            wallDistLUT[i] = renderHeight / ((2.0f * y) - renderHeight);
        }
    }

    @Override
    protected void onDraw(final Canvas canvas)
    {
        preRender(canvas);
        while (!rendering);
        render(canvas);
        rendering = false;
        rendering = false;
        drawControls(canvas);
    }

    private void drawControls(final Canvas canvas) {
        paint.setColor(0xbb999999);
        int moveCircleX = (int)(moveControl.loc.x * width);
        int moveCircleY = (int)(moveControl.loc.y * height);
        int moveRadius = (int)(moveControl.radius * biggerDim);
        canvas.drawCircle(moveCircleX, moveCircleY, moveRadius, paint);

        int lookCircleX = (int)(lookControl.loc.x * width);
        int lookCircleY = (int)(lookControl.defaultLoc.y * height);
        int lookRadius = (int)(lookControl.radius * biggerDim);
        canvas.drawCircle(lookCircleX, lookCircleY, lookRadius, paint);
    }

    private static final float yTouchThreshold = 0.3f;
    private static final float xTouchThreshold = 0.35f;
    private static final float moveSpeed = 0.05f;
    private static final float turnSpeed = 0.05f;

    public void updateInput()
    {
        if (mouseLoc != null) {
            float x = mouseLoc.x;
            float y = mouseLoc.y;

            if (y < yTouchThreshold) {
                moveForward(moveSpeed);
            } else if (y > 1 - yTouchThreshold) {
                moveBackward(moveSpeed);
            }

            if (x < xTouchThreshold) {
                rotateLeft(turnSpeed);
            } else if (x > 1 - xTouchThreshold) {
                rotateRight(turnSpeed);
            }

            invalidate();
        }
    }

    private volatile Point mouseLoc = null;

    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        int action = event.getAction();
        switch(action) {
            case MotionEvent.ACTION_DOWN:
                float x = event.getX()/width;
                float y = event.getY()/height;
                //mouseLoc = new Point(x, y);
                break;
            case MotionEvent.ACTION_UP:
                mouseLoc = null;
                break;
        }

        moveControl.handleTouchEvent(event);
        lookControl.handleTouchEvent(event);

        invalidate();

        return true;
    }

    private void moveBackward(float f) {
        float x = pos.x - dir.x * f;
        float y = pos.y - dir.y * f;
        pos.x = x;
        pos.y = y;
    }

    private void moveForward(float f) {
        float x = pos.x + dir.x * f;
        float y = pos.y + dir.y * f;
        pos.x = x;
        pos.y = y;
    }

    private void moveRight(float f) {
        pos.x = pos.x + sidedir.x * f;
        pos.y = pos.y + sidedir.y * f;
    }

    private void rotateLeft(float factor) {
        float speed = factor;

        float oldDirX = dir.x;
        float cr = (float) Math.cos(speed);
        float sr = (float) Math.sin(speed);
        float dirx = dir.x * cr - dir.y * sr;
        float diry = oldDirX * sr + dir.y * cr;
        dir.x = dirx;
        dir.y = diry;

        float oldSideDirX = sidedir.x;
        float sidedirx = sidedir.x * cr - sidedir.y * sr;
        float sidediry = oldSideDirX * sr + sidedir.y * cr;
        sidedir.x = sidedirx;
        sidedir.y = sidediry;

        float oldScreenX = screen.x;
        float screenx = screen.x * cr - screen.y * sr;
        float screeny = oldScreenX * sr + screen.y * cr;
        screen.x = screenx;
        screen.y = screeny;
    }

    private void rotateRight(float factor) {
        float speed = factor;

        float oldDirX = dir.x;
        float cr = (float) Math.cos(-speed);
        float sr = (float) Math.sin(-speed);
        float dirx = dir.x * cr - dir.y * sr;
        float diry = oldDirX * sr + dir.y * cr;
        dir.x = dirx;
        dir.y = diry;

        float oldSideDirX = sidedir.x;
        float sidedirx = sidedir.x * cr - sidedir.y * sr;
        float sidediry = oldSideDirX * sr + sidedir.y * cr;
        sidedir.x = sidedirx;
        sidedir.y = sidediry;

        float oldScreenX = screen.x;
        float screenx = screen.x * cr - screen.y * sr;
        float screeny = oldScreenX * sr + screen.y * cr;
        screen.x = screenx;
        screen.y = screeny;
    }

    private void preRender(final Canvas canvas) {
        paint.setColor(0xff000000);
        canvas.drawRect(0, 0, width, height, paint);

        for (int i = 0; i < zbuf.length; i++) { //Reset the z buffer
            zbuf[i] = Float.POSITIVE_INFINITY;
        }

        for (int i = 0;i < pixels.length; i++) { //Reset pixel buffer instead of reallocating memory
            pixels[i] = 0;
        }
    }

    private void render(final Canvas canvas) {
        latchRef.latch = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            renderers[i].startRendering();
        }

        try {
            latchRef.latch.await();
        } catch (Exception e) {
            e.printStackTrace();
        }

        target.setPixels(pixels, 0, renderWidth, 0, 0, renderWidth, renderHeight);
        canvas.drawBitmap(Bitmap.createScaledBitmap(target, width, height, false), 0, 0, paint);
    }

    private static class ThreadRenderer implements Runnable {
        private final int[] pixels;
        private final List<Line> lines;
        private final float[] zbuf;
        private final float[] wallDistLUT;
        private final Bitmap floorTexture, ceilTexture;
        private final Point pos;
        private final Point dir;
        private final Point screen;
        private final int renderWidth, renderHeight;
        private final int startX;
        private final int endX;
        private final LatchRef latchRef;
        private volatile boolean isRunning = true;
        private volatile boolean startRendering = false;

        public ThreadRenderer(final int[] pixelsIn, final float[] zbufIn, final float[] wallDistLUTIn, final Bitmap floorTextureIn, final Bitmap ceilTextureIn, final Point posIn, final Point dirIn, final Point screenIn, int renderWidthIn, int renderHeightIn, final List<Line> linesIn, int startXIn, int endXIn, final LatchRef latchRefIn) {
            pixels = pixelsIn;
            lines = linesIn;
            zbuf = zbufIn;
            wallDistLUT = wallDistLUTIn;
            floorTexture = floorTextureIn;
            ceilTexture = ceilTextureIn;
            pos = posIn;
            dir = dirIn;
            screen = screenIn;
            renderWidth = renderWidthIn;
            renderHeight = renderHeightIn;
            startX = startXIn;
            endX = endXIn;
            latchRef = latchRefIn;
        }

        public void startRendering() {
            startRendering = true;
        }

        @Override
        public void run()
        {
            while(isRunning) {
                if (startRendering) {
                    for (int x = startX; x < endX; x++) {
                        float xNorm = 2.0f * x / (float) renderWidth - 1.0f;

                        float dirX = dir.x + screen.x * xNorm;
                        float dirY = dir.y + screen.y * xNorm;

                        Point rayDir = new Point(pos.x + dirX, pos.y + dirY);

                        int startY = 0;
                        int lineHeight = 0;
                        float dist = 0;

                        float perpWallDist = 0;

                        Point closeHit = null;
                        for (Line wall : lines) {
                            Point hit = rayHitSegment(pos, rayDir, wall);
                            if (hit != null) {
                                dist = distance(hit, pos);
                                if (dist < zbuf[x]) {
                                    zbuf[x] = dist;
                                    closeHit = new Point(hit.x, hit.y);

                                    if (dirX == 0) {
                                        perpWallDist = dist;
                                    } else {
                                        perpWallDist = ((closeHit.x - pos.x + (1 - 1) / 2)) / dirX;
                                    }

                                    lineHeight = (int)(renderHeight / perpWallDist);
                                    startY = (renderHeight - lineHeight) / 2;

                                    float texNorm = distance(wall.p0, hit)/wall.length;
                                    texNorm -= Math.floor(texNorm);
                                    int texX = (int)(texNorm * wall.texture.getWidth() * wall.xTiles) % wall.texture.getWidth();

                                    int startY2 = (startY < 0) ? 0 : startY;
                                    int endY = (startY + lineHeight >= renderHeight) ? renderHeight - 1 : startY + lineHeight;

                                    for (int y = startY2; y < endY; y++) {
                                        float normY = (y - startY)/(float)lineHeight;
                                        int texY = Math.abs((int)(wall.texture.getHeight() * normY * wall.yTiles) % wall.texture.getHeight());
                                        int color = wall.texture.getPixel(texX, texY);

                                        pixels[x + y * renderWidth] = darken(color, dist);
                                    }
                                }
                            }
                        }

                        if (closeHit != null) {
                            for (int y = (startY + lineHeight); y < renderHeight; y++) {
                                float currentDist = wallDistLUT[y - (renderHeight/2) - 1];

                                float weight = (currentDist) / (perpWallDist);

                                float currentFloorX = weight * closeHit.x + (1.0f - weight) * pos.x;
                                float currentFloorY = weight * closeHit.y + (1.0f - weight) * pos.y;

                                int floorTexX = (int) (currentFloorX * floorTexture.getWidth()) % floorTexture.getWidth();
                                int floorTexY = (int) (currentFloorY * floorTexture.getHeight()) % floorTexture.getHeight();

                                if (floorTexX >= 0 && floorTexY >= 0) {
                                    int color = floorTexture.getPixel(floorTexX, floorTexY);
                                    pixels[x + y * renderWidth] = darken(color, currentDist);

                                    color = ceilTexture.getPixel(floorTexX, floorTexY);
                                    pixels[x + (renderHeight - y) * renderWidth] = darken(color, currentDist);
                                }
                            }
                        }
                    }

                    startRendering = false;
                    latchRef.latch.countDown();
                }
            }
        }
    }

    private static final class LatchRef {
        public volatile CountDownLatch latch;

        public LatchRef(final CountDownLatch latchIn) {
            latch = latchIn;
        }
    }

    private static int darken(int color, float dist) {
        int red = (color >>> 16) & 0xff;
        int green = (color >>> 8) & 0xff;
        int blue = color & 0xff;

        float f = 0;
        if (dist >= FOG_LIMIT) {
            f = 1.0f;
        } else {
            f = dist/FOG_LIMIT;
        }

        red *= (1 - f);
        green *= (1 - f);
        blue *= (1 - f);
        return 0xff000000 | (red << 16) | (green << 8) | blue;
    }

    public static class Line {
        public final Point p0, p1;
        public final float length;
        public final float lensqr;
        public final Point normal;
        public final Bitmap texture;
        public final int xTiles;
        public final int yTiles;
        public final float xDiff;
        public final float yDiff;

        public Line(final Point p0In, final Point p1In, final Bitmap textureIn, int xTilesIn, int yTilesIn) {
            p0 = p0In;
            p1 = p1In;
            texture = textureIn;
            xTiles = xTilesIn;
            yTiles = yTilesIn;

            length = distance(p0, p1);
            lensqr = length * length;
            xDiff = p1.x - p0.x;
            yDiff = p1.y - p0.y;

            normal = new Point(-yDiff, xDiff);
            float normLen = distance(normal, new Point(0, 0));
            normal.x /= normLen;
            normal.y /= normLen;
        }

        public Line(final Point p0In, final Point p1In, final Bitmap textureIn) {
            this(p0In, p1In, textureIn, 1, 1);
        }
    }

    public static class Point {
        public float x, y;

        public Point(float _x, float _y) {
            x = _x;
            y = _y;
        }

        public float dot(final Point p) {
            return (x * p.x) + (y * p.y);
        }
    }

    public static float distSqr(final Point p0, final Point p1) {
        return ((p0.x - p1.x) * (p0.x - p1.x)) + ((p0.y - p1.y) * (p0.y - p1.y));
    }

    public static float distance(final Point p0, final Point p1) {
        return (float)Math.sqrt(distSqr(p0, p1));
    }

    public static Point rayHitSegment(final Point rayStart, final Point rayDirection, final Line segment) {
        Point r0 = rayStart;
        Point r1 = rayDirection;
        Point a = segment.p0;
        Point b = segment.p1;

        Point s1, s2;
        s1 = new Point(r1.x - r0.x, r1.y - r0.y);
        s2 = new Point(b.x - a.x, b.y - a.y);

        float s, t;
        s = (-s1.y * (r0.x - a.x) + s1.x * (r0.y - a.y)) / (-s2.x * s1.y + s1.x * s2.y);
        t = (s2.x * (r0.y - a.y) - s2.y * (r0.x - a.x)) / (-s2.x * s1.y + s1.x * s2.y);

        if (s >= 0 && s <= 1 && t >= 0) {
            return new Point(r0.x + (t * s1.x), r0.y + (t * s1.y));
        }
        return null;
    }

    public static float angleBetweenLines(final Line wall1, final Line wall2) {
        float angle1 = (float) Math.atan2(wall1.p0.y - wall1.p1.y, wall1.p0.x - wall1.p1.x);
        float angle2 = (float) Math.atan2(wall2.p0.y - wall2.p1.y, wall2.p0.x - wall2.p1.x);

        return Math.abs(angle1-angle2);
    }

    private final class MoveControl extends CircleControl {
        private static final float SPEEDMULT = 0.9f;

        public MoveControl(final Point defaultLocIn, float radiusIn, float thresholdIn) {
            super(defaultLocIn, radiusIn, thresholdIn);
        }

        @Override
        protected void handleMovement() {
            //moveForward(defaultLoc.y - loc.y);
            //moveRight((loc.x - defaultLoc.x) * 0.4f);
            final float xFactor = (loc.x - defaultLoc.x);
            final float yFactor = (defaultLoc.y - loc.y);

            float x0 = yFactor * dir.x;
            float y0 = yFactor * dir.y;

            float x1 = xFactor * sidedir.x;
            float y1 = xFactor * sidedir.y;

            final float x = x0 + x1;
            final float y = y0 + y1;
            final Point vel = new Point(x * SPEEDMULT, y * SPEEDMULT);
            final Point endPos = Fizix.moveInWorld(pos, vel, lines, PLAYER_RADIUS, SPACING, 2);
            pos.x = endPos.x;
            pos.y = endPos.y;
        }
    }

    private final class LookControl extends CircleControl {

        public LookControl(final Point defaultLocIn, float radiusIn, float thresholdIn) {
            super(defaultLocIn, radiusIn, thresholdIn);
        }

        @Override
        protected void handleMovement() {
            rotateRight((loc.x - defaultLoc.x));
        }
    }

    private abstract class CircleControl {
        protected volatile Point loc;
        private final float radius;
        private final float threshold;
        protected final Point defaultLoc;
        public volatile boolean grabbed = false;
        private int pointerID = -1;

        public CircleControl(final Point defaultLocIn, float radiusIn, float thresholdIn) {
            defaultLoc = defaultLocIn;
            loc = defaultLoc;
            radius = radiusIn;
            threshold = thresholdIn;
        }

        protected abstract void handleMovement();

        protected void handleTouchEvent(MotionEvent event) {
            int action = event.getAction() & 0xff; //equivalent of MotionEventCompat.getActionMasked()
            Point eventLoc = new Point(event.getX(event.getActionIndex())/width, event.getY(event.getActionIndex())/height);

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (pointerID == -1 && distance(eventLoc, defaultLoc) <= radius) {
                        grabbed = true;
                        pointerID = event.getPointerId(event.getActionIndex());
                    }
                    break;
                case MotionEvent.ACTION_MOVE:

                    if (grabbed && pointerID != -1) {
                        int index = event.findPointerIndex(pointerID);
                        eventLoc = new Point(event.getX(index)/width, event.getY(index)/height);
                        if (distance(eventLoc, defaultLoc) <= threshold) {
                            loc = eventLoc;
                        }
                        //handleMovement();
                    }
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_UP:
                    if (pointerID != -1 && event.getActionIndex() == event.findPointerIndex(pointerID)) {
                        grabbed = false;
                        pointerID = -1;
                        loc = defaultLoc;
                    }
                    break;
            }
        }
    }
}
