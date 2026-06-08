// SPDX-License-Identifier: WTFPL
package aenu.ax360e;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class VirtualControl extends android.view.SurfaceView implements View.OnTouchListener{

    /*  xe::ui::VirtualKey::kXInputPadDpadLeft,
            xe::ui::VirtualKey::kXInputPadDpadUp,
            xe::ui::VirtualKey::kXInputPadDpadRight,
            xe::ui::VirtualKey::kXInputPadDpadDown,
            xe::ui::VirtualKey::kXInputPadA,
            xe::ui::VirtualKey::kXInputPadB,
            xe::ui::VirtualKey::kXInputPadX,
            xe::ui::VirtualKey::kXInputPadY,
            xe::ui::VirtualKey::kXInputPadBack,
            xe::ui::VirtualKey::kXInputPadStart,

            xe::ui::VirtualKey::kXInputPadLShoulder,
            xe::ui::VirtualKey::kXInputPadRShoulder,
            xe::ui::VirtualKey::kXInputPadLThumbPress,
            xe::ui::VirtualKey::kXInputPadRThumbPress,
            xe::ui::VirtualKey::kXInputPadLTrigger,
            xe::ui::VirtualKey::kXInputPadRTrigger,

            xe::ui::VirtualKey::kXInputPadLThumbLeft,
            xe::ui::VirtualKey::kXInputPadLThumbUp,
            xe::ui::VirtualKey::kXInputPadLThumbRight,
            xe::ui::VirtualKey::kXInputPadLThumbDown,
            xe::ui::VirtualKey::kXInputPadRThumbLeft,
            xe::ui::VirtualKey::kXInputPadRThumbUp,
            xe::ui::VirtualKey::kXInputPadRThumbRight,
            xe::ui::VirtualKey::kXInputPadRThumbDown
     */

    public static final int KEY_CODE_DPAD_LEFT=0;
    public static final int KEY_CODE_DPAD_UP=1;
    public static final int KEY_CODE_DPAD_RIGHT=2;
    public static final int KEY_CODE_DPAD_DOWN=3;
    public static final int KEY_CODE_A=4;
    public static final int KEY_CODE_B=5;
    public static final int KEY_CODE_X=6;
    public static final int KEY_CODE_Y=7;
    public static final int KEY_CODE_BACK=8;
    public static final int KEY_CODE_START=9;

    public static final int KEY_CODE_SHOULDER_L=10;
    public static final int KEY_CODE_SHOULDER_R=11;
    public static final int KEY_CODE_THUMB_PRESS_L=12;
    public static final int KEY_CODE_THUMB_PRESS_R=13;
    public static final int KEY_CODE_TRIGGER_L=14;
    public static final int KEY_CODE_TRIGGER_R=15;

    public static final int KEY_CODE_LTHUMB_LEFT=16;
    public static final int KEY_CODE_LTHUMB_UP=17;
    public static final int KEY_CODE_LTHUMB_RIGHT=18;
    public static final int KEY_CODE_LTHUMB_DOWN=19;
    public static final int KEY_CODE_RTHUMB_LEFT=20;
    public static final int KEY_CODE_RTHUMB_UP=21;
    public static final int KEY_CODE_RTHUMB_RIGHT=22;
    public static final int KEY_CODE_RTHUMB_DOWN=23;

    public static final short KEY_VALUE_UNUSED=-1;

    interface GamepadEventListener{
        public void send_key_event(int key_code,boolean pressed,short value);
    }

    interface Component{

        String get_name();
        double get_ratio();

        int get_x();
        int get_y();
        double get_scale();
        double get_opacity();

        void set_x(int x);
        void set_y(int y);
        void set_scale(double scale);
        void set_opacity(double opacity);
        void set_gamepad_event_listener(GamepadEventListener listener);
        void draw(Canvas canvas);

        void on_touch(MotionEvent event,int left_joystick_pointer_id,int right_joystick_pointer_id);
    }

    static int get_width_height(Context context,double  ratio){
        DisplayMetrics dm=context.getResources().getDisplayMetrics();
        int min_dimension=Math.min(dm.widthPixels,dm.heightPixels);
        return (int)(min_dimension*ratio);
    }
    static Bitmap create_ratio_bitmap(Context context,int res_id,double ratio){
        DisplayMetrics dm=context.getResources().getDisplayMetrics();
        int min_dimension=Math.min(dm.widthPixels,dm.heightPixels);
        return Bitmap.createScaledBitmap(BitmapFactory.decodeResource(context.getResources(),res_id),
                (int)(min_dimension*ratio),
                (int)(min_dimension*ratio),
                true);
    }

    static int offset_x(Context context,double hp){
        DisplayMetrics dm=context.getResources().getDisplayMetrics();
        return (int)(dm.widthPixels*hp);
    }

    static int offset_y(Context context,double vp){
        DisplayMetrics dm=context.getResources().getDisplayMetrics();
        return (int)(dm.heightPixels*vp);
    }

    static double offset_ratio(Context context){
        DisplayMetrics dm=context.getResources().getDisplayMetrics();
        int min_dimension=Math.min(dm.widthPixels,dm.heightPixels);
        int max_dimension=Math.max(dm.widthPixels,dm.heightPixels);
        return (double)min_dimension/(double)max_dimension;
    }

    class Dpad implements Component{

        final String name;

        int res_id;
        int pressed_res_id;
        int pressed_2_res_id;

        final double ratio;

        double scale=1.0f;
        double opacity=1.0f;

        Bitmap bitmap;
        Bitmap pressed_bitmap;
        Bitmap pressed_2_bitmap;

        GamepadEventListener key_listener;

        Rect rect;
        Paint paint;
        Matrix matrix;

        Context context;

        static final int PRESSED_STATUS_NONE=0;
        static final int PRESSED_STATUS_LEFT=1;
        static final int PRESSED_STATUS_UP=2;
        static final int PRESSED_STATUS_RIGHT=3;
        static final int PRESSED_STATUS_DOWN=4;
        static final int PRESSED_STATUS_LEFT_UP=5;
        static final int PRESSED_STATUS_LEFT_DOWN=6;
        static final int PRESSED_STATUS_RIGHT_UP=7;
        static final int PRESSED_STATUS_RIGHT_DOWN=8;

        int pressed_status=PRESSED_STATUS_NONE;

        int position_x;//left
        int position_y;//up

        public Dpad(String name,Context context,int res_id,int pressed_res_id,int pressed_2_res_id,double ratio,int x,int y){
            this.name=name;
            this.context=context;
            this.res_id=res_id;
            this.pressed_res_id=pressed_res_id;
            this.pressed_2_res_id=pressed_2_res_id;
            this.ratio=ratio;

            rect=new Rect();
            paint=new Paint();
            matrix=new Matrix();

            position_x=x;
            position_y=y;

            setup_bitmap();
            set_scale(1.f);
            set_opacity(1.f);
            setup_rect();
        }

        void setup_bitmap(){
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            if (pressed_bitmap != null && !pressed_bitmap.isRecycled()) pressed_bitmap.recycle();
            if (pressed_2_bitmap != null && !pressed_2_bitmap.isRecycled()) pressed_2_bitmap.recycle();
            bitmap=VirtualControl.create_ratio_bitmap(context,res_id,ratio* scale);
            pressed_bitmap=VirtualControl.create_ratio_bitmap(context,pressed_res_id,ratio* scale);
            pressed_2_bitmap=VirtualControl.create_ratio_bitmap(context,pressed_2_res_id,ratio* scale);
        }

        void setup_rect(){
            rect.set(position_x,position_y,position_x+bitmap.getWidth(),position_y+bitmap.getHeight());
        }

        @Override
        public String get_name() {
            return name;
        }

        @Override
        public double get_ratio() {
            return ratio;
        }

        @Override
        public int get_x() {
            return position_x;
        }

        @Override
        public int get_y() {
            return position_y;
        }

        @Override
        public double get_scale() {
            return scale;
        }

        @Override
        public double get_opacity() {
            return opacity;
        }

        @Override
        public void set_x(int x) {
            this.position_x=x;
        }

        @Override
        public void set_y(int y) {
            this.position_y=y;
        }

        @Override
        public void set_gamepad_event_listener(GamepadEventListener listener) {
            this.key_listener=listener;
        }

        @Override
        public void set_scale(double scale) {
            this.scale=scale;
            setup_bitmap();
            setup_rect();
        }

        @Override
        public void set_opacity(double opacity) {
            this.opacity=opacity;
            paint.setAlpha((int)(opacity*255));
        }

        @Override
        public void draw(Canvas canvas) {
            final int bmp_x = position_x;
            final int bmp_y = position_y;
            final int bmp_w = bitmap.getWidth();
            final int bmp_h = bitmap.getHeight();
            switch (pressed_status) {
                case PRESSED_STATUS_NONE:
                    canvas.drawBitmap(bitmap, bmp_x, bmp_y, paint);
                    break;
                case PRESSED_STATUS_LEFT:
                    canvas.drawBitmap(pressed_bitmap, bmp_x, bmp_y, paint);
                    break;
                case PRESSED_STATUS_UP:
                    matrix.reset();
                    matrix.postRotate(90, bmp_w / 2f, bmp_h / 2f);
                    matrix.postTranslate(bmp_x, bmp_y);
                    canvas.drawBitmap(pressed_bitmap, matrix, paint);
                    break;
                case PRESSED_STATUS_RIGHT:
                    matrix.reset();
                    matrix.postRotate(180, bmp_w / 2f, bmp_h / 2f);
                    matrix.postTranslate(bmp_x, bmp_y);
                    canvas.drawBitmap(pressed_bitmap, matrix, paint);
                    break;
                case PRESSED_STATUS_DOWN:
                    matrix.reset();
                    matrix.postRotate(270, bmp_w / 2f, bmp_h / 2f);
                    matrix.postTranslate(bmp_x, bmp_y);
                    canvas.drawBitmap(pressed_bitmap, matrix, paint);
                    break;
                case PRESSED_STATUS_LEFT_UP:
                    canvas.drawBitmap(pressed_2_bitmap, bmp_x, bmp_y, paint);
                    break;
                case PRESSED_STATUS_LEFT_DOWN:
                    matrix.reset();
                    matrix.postRotate(270, bmp_w / 2f, bmp_h / 2f);
                    matrix.postTranslate(bmp_x, bmp_y);
                    canvas.drawBitmap(pressed_2_bitmap, matrix, paint);
                    break;
                case PRESSED_STATUS_RIGHT_UP:
                    matrix.reset();
                    matrix.postRotate(90, bmp_w / 2f, bmp_h / 2f);
                    matrix.postTranslate(bmp_x, bmp_y);
                    canvas.drawBitmap(pressed_2_bitmap, matrix, paint);
                    break;
                case PRESSED_STATUS_RIGHT_DOWN:
                    matrix.reset();
                    matrix.postRotate(180, bmp_w / 2f, bmp_h / 2f);
                    matrix.postTranslate(bmp_x, bmp_y);
                    canvas.drawBitmap(pressed_2_bitmap, matrix, paint);
                    break;
            }
        }

        void send_key_event(){
            if(key_listener!=null){
                switch (pressed_status){

                    case PRESSED_STATUS_NONE:
                        key_listener.send_key_event(KEY_CODE_DPAD_LEFT,false,KEY_VALUE_UNUSED);
                        key_listener.send_key_event(KEY_CODE_DPAD_UP,false, KEY_VALUE_UNUSED);
                        key_listener.send_key_event(KEY_CODE_DPAD_RIGHT,false, KEY_VALUE_UNUSED);
                        key_listener.send_key_event(KEY_CODE_DPAD_DOWN,false, KEY_VALUE_UNUSED);
                        break;
                    case PRESSED_STATUS_LEFT:
                        key_listener.send_key_event(KEY_CODE_DPAD_LEFT,true, KEY_VALUE_UNUSED);
                        key_listener.send_key_event(KEY_CODE_DPAD_UP,false, KEY_VALUE_UNUSED);
                        key_listener.send_key_event(KEY_CODE_DPAD_RIGHT,false, KEY_VALUE_UNUSED);
                        key_listener.send_key_event(KEY_CODE_DPAD_DOWN,false, KEY_VALUE_UNUSED);
                        break;
                    case PRESSED_STATUS_UP:
                        key_listener.send_key_event(KEY_CODE_DPAD_LEFT,false, KEY_VALUE_UNUSED);
                        key_listener.send_key_event(KEY_CODE_DPAD_UP,true, KEY_VALUE_UNUSED);
                        key_listener.send_key_event(KEY_CODE_DPAD_RIGHT,false, KEY_VALUE_UNUSED);
                        key_listener.send_key_event(KEY_CODE_DPAD_DOWN,false, KEY_VALUE_UNUSED);
                        break;
                    case PRESSED_STATUS_RIGHT:
                        key_listener.send_key_event(KEY_CODE_DPAD_LEFT,false, KEY_VALUE_UNUSED);
                        key_listener.send_key_event(KEY_CODE_DPAD_UP,false, KEY_VALUE_UNUSED);
                        key_listener.send_key_event(KEY_CODE_DPAD_RIGHT,true, KEY_VALUE_UNUSED);
                        key_listener.send_key_event(KEY_CODE_DPAD_DOWN,false, KEY_VALUE_UNUSED);
                        break;
                    case PRESSED_STATUS_DOWN:
                        key_listener.send_key_event(KEY_CODE_DPAD_LEFT,false, KEY_VALUE_UNUSED);
                        key_listener.send_key_event(KEY_CODE_DPAD_UP,false, KEY_VALUE_UNUSED);
                        key_listener.send_key_event(KEY_CODE_DPAD_RIGHT,false, KEY_VALUE_UNUSED);
                        key_listener.send_key_event(KEY_CODE_DPAD_DOWN,true, KEY_VALUE_UNUSED);
                        break;

                    case PRESSED_STATUS_LEFT_UP:
                        key_listener.send_key_event(KEY_CODE_DPAD_LEFT,true , KEY_VALUE_UNUSED);
                        key_listener.send_key_event(KEY_CODE_DPAD_UP,true, KEY_VALUE_UNUSED);
                        key_listener.send_key_event(KEY_CODE_DPAD_RIGHT,false, KEY_VALUE_UNUSED);
                        key_listener.send_key_event(KEY_CODE_DPAD_DOWN,false, KEY_VALUE_UNUSED);
                        break;
                    case PRESSED_STATUS_LEFT_DOWN:
                        key_listener.send_key_event(KEY_CODE_DPAD_LEFT,true, KEY_VALUE_UNUSED);
                        key_listener.send_key_event(KEY_CODE_DPAD_UP,false, KEY_VALUE_UNUSED);
                        key_listener.send_key_event(KEY_CODE_DPAD_RIGHT,false, KEY_VALUE_UNUSED);
                        key_listener.send_key_event(KEY_CODE_DPAD_DOWN,true, KEY_VALUE_UNUSED);
                        break;
                    case PRESSED_STATUS_RIGHT_UP:
                        key_listener.send_key_event(KEY_CODE_DPAD_LEFT,false, KEY_VALUE_UNUSED);
                        key_listener.send_key_event(KEY_CODE_DPAD_UP,true, KEY_VALUE_UNUSED);
                        key_listener.send_key_event(KEY_CODE_DPAD_RIGHT,true, KEY_VALUE_UNUSED);
                        key_listener.send_key_event(KEY_CODE_DPAD_DOWN,false, KEY_VALUE_UNUSED);
                        break;
                    case PRESSED_STATUS_RIGHT_DOWN:
                        key_listener.send_key_event(KEY_CODE_DPAD_LEFT,false, KEY_VALUE_UNUSED);
                        key_listener.send_key_event(KEY_CODE_DPAD_UP,false, KEY_VALUE_UNUSED);
                        key_listener.send_key_event(KEY_CODE_DPAD_RIGHT,true, KEY_VALUE_UNUSED);
                        key_listener.send_key_event(KEY_CODE_DPAD_DOWN,true, KEY_VALUE_UNUSED);
                        break;
                }
            }
        }

        boolean up_pressed_status(int x,int y,int space){
            int last_pressed_status=pressed_status;
            if(x==-1&&y==-1){
                pressed_status=PRESSED_STATUS_NONE;
                return last_pressed_status!=pressed_status;
            }
            boolean l,u,r,d;
            l=u=r=d=false;
            if(x>=position_x&&x<=position_x+space){
                l=true;
            }
            if(x>=position_x+space*2&&x<=position_x+3*space){
                r=true;
            }
            if(y>=position_y&&y<=position_y+space){
                u=true;
            }
            if(y>=position_y+space*2&&y<=position_y+3*space){
                d=true;
            }

            if(l&&u){
                pressed_status=PRESSED_STATUS_LEFT_UP;
            }else if(l&&d){
                pressed_status=PRESSED_STATUS_LEFT_DOWN;
            }else if(r&&u){
                pressed_status=PRESSED_STATUS_RIGHT_UP;
            }else if(r&&d){
                pressed_status=PRESSED_STATUS_RIGHT_DOWN;
            }else if(l){
                pressed_status=PRESSED_STATUS_LEFT;
            }else if(u){
                pressed_status=PRESSED_STATUS_UP;
            }else if(r){
                pressed_status=PRESSED_STATUS_RIGHT;
            }else if(d){
                pressed_status=PRESSED_STATUS_DOWN;
            }
            return last_pressed_status!=pressed_status;
        }

        @Override
        public void on_touch(MotionEvent event,int left_joystick_pointer_id,int right_joystick_pointer_id) {
            int space=(int)(rect.width()/3.0f);
            boolean miss=true;
            if(event.getActionMasked()!=MotionEvent.ACTION_UP)
            for(int i=0;i<event.getPointerCount();i++){
                int pointer_id=event.getPointerId(i);
                if(pointer_id!=-1&&(pointer_id==left_joystick_pointer_id||pointer_id==right_joystick_pointer_id)) continue;
                int x=(int)event.getX(i);
                int y=(int)event.getY(i);
                if(rect.contains(x,y)){
                    miss=false;
                    boolean changed=up_pressed_status(x,y,space);
                    if( changed) invalidate();
                    send_key_event();
                }
            }
            if(miss) if(up_pressed_status(-1,-1,space)) invalidate();
            send_key_event();
        }

    }

    //PS3 ᐃ口OX SELECT START L1,L2,L3 R1,R2,R3
    //XBOX360 YXBA BACK START LB,RB,LT,RT

    class Button implements Component{

        final String name;

        Context context;

        final double ratio;

        double scale=1.0f;
        double opacity=1.0f;

        int button_key_code;

        int res_id;
        //int pressed_res_id;

        Bitmap bitmap;
        Bitmap pressed_bitmap;

        GamepadEventListener key_listener;

        Rect rect;
        Paint paint;

        int position_x;
        int position_y;

        int pressed_status;
        static final int PRESSED_STATUS_NONE=0;
        static final int PRESSED_STATUS_PRESSED=1;

        public Button(String name,Context context, int res_id, /*int pressed_res_id,*/double ratio,int x,int y,int button_key_code){
            this.name=name;
            this.context=context;
            this.res_id=res_id;
            //this.pressed_res_id=pressed_res_id;
            this.ratio=ratio;
            this.position_x=x;
            this.position_y=y;
            this.button_key_code=button_key_code;

            paint=new Paint();
            rect = new Rect();

            setup_bitmap();
            set_scale(1.f);
            set_opacity(1.f);
            setup_rect();
        }

        void setup_bitmap(){
            bitmap=create_ratio_bitmap(context,res_id,ratio* scale);
            //pressed_bitmap=create_ratio_bitmap(context,pressed_res_id,ratio* scale);
            pressed_bitmap=Utils.gen_pressed_bitmap(bitmap);
        }

        void setup_rect(){
            rect.set(position_x,position_y,position_x+bitmap.getWidth(),position_y+bitmap.getHeight());
        }

        @Override
        public String get_name() {
            return name;
        }

        @Override
        public double get_ratio() {
            return ratio;
        }

        @Override
        public int get_x() {
            return position_x;
        }

        @Override
        public int get_y() {
            return position_y;
        }

        @Override
        public double get_scale() {
            return scale;
        }

        @Override
        public double get_opacity() {
            return opacity;
        }

        @Override
        public void set_x(int x) {
            this.position_x=x;
        }

        @Override
        public void set_y(int y) {
            this.position_y=y;
        }

        @Override
        public void set_gamepad_event_listener(GamepadEventListener listener) {
            key_listener=listener;
        }

        @Override
        public void set_scale(double scale) {
            this.scale=scale;
            setup_bitmap();
            setup_rect();
        }

        @Override
        public void set_opacity(double opacity) {
            this.opacity=opacity;
            paint.setAlpha((int)(opacity*255));
        }

        @Override
        public void draw(Canvas canvas) {
            if(pressed_status==PRESSED_STATUS_PRESSED) canvas.drawBitmap(pressed_bitmap,position_x,position_y,paint);
            else canvas.drawBitmap(bitmap,position_x,position_y,paint);
        }

        @Override
        public void on_touch(MotionEvent event,int left_joystick_pointer_id,int right_joystick_pointer_id) {
            boolean miss=true;
            if(event.getActionMasked()!=MotionEvent.ACTION_UP)
            for(int i=0;i<event.getPointerCount();i++){
                int pointer_id=event.getPointerId(i);
                if(pointer_id!=-1&&(pointer_id==left_joystick_pointer_id||pointer_id==right_joystick_pointer_id)) continue;
                int x=(int)event.getX(i);
                int y=(int)event.getY(i);
                if(rect.contains(x,y)){
                    miss=false;
                    int last_pressed_status=pressed_status;
                    pressed_status=PRESSED_STATUS_PRESSED;
                    if(last_pressed_status!=pressed_status)
                        invalidate();
                    send_key_event();
                }
            }
            if(miss) {
                int last_pressed_status=pressed_status;
                pressed_status=PRESSED_STATUS_NONE;
                if(last_pressed_status!=pressed_status)
                    invalidate();
                send_key_event();
            }
        }

        void send_key_event(){
            if(key_listener!=null)
                key_listener.send_key_event(button_key_code,pressed_status==PRESSED_STATUS_PRESSED,KEY_VALUE_UNUSED);
        }
    }

    class Joystick implements Component {

        final String name;

        Context context;
        int res_id;
        int pressed_res_id;
        int large_res_id;

        final double ratio;
        final double inner_ratio;
        double scale = 1.0f;
        double opacity = 1.0f;

        Paint paint;
        Rect rect;

        int position_x;
        int position_y;

        boolean pressed;

        double pressed_x;
        double pressed_y;

        Bitmap bitmap;
        Bitmap pressed_bitmap;
        Bitmap large_bitmap;

        GamepadEventListener key_listener;

        int joystick_pointer_id=-1;
        boolean is_left_joystick;

        public Joystick(String name,Context context, int res_id, int pressed_res_id, int large_res_id, double ratio, int x, int y,boolean is_left_joystick) {
            this.name = name;
            this.context = context;
            this.res_id = res_id;
            this.pressed_res_id = pressed_res_id;
            this.large_res_id = large_res_id;
            this.ratio = ratio;
            this.inner_ratio = ratio * 0.5f;
            this.position_x = x;
            this.position_y = y;
            this.is_left_joystick = is_left_joystick;

            paint = new Paint();
            rect = new Rect();

            setup_bitmap();
            set_scale(1.f);
            set_opacity(1.f);
            setup_rect();
        }

        void setup_bitmap() {
            bitmap = create_ratio_bitmap(context, res_id, inner_ratio * scale);
            pressed_bitmap = create_ratio_bitmap(context, pressed_res_id, inner_ratio * scale);
            large_bitmap = create_ratio_bitmap(context, large_res_id, ratio * scale);
        }

        void setup_rect() {
            rect.set(position_x, position_y, position_x + large_bitmap.getWidth(), position_y + large_bitmap.getHeight());
        }

        void send_key_event() {
            final short _0=0;
            if (key_listener != null) {
                if(is_left_joystick){
                    if(!pressed){
                        key_listener.send_key_event(KEY_CODE_LTHUMB_LEFT, false,_0);
                        key_listener.send_key_event(KEY_CODE_LTHUMB_UP, false,_0);
                        key_listener.send_key_event(KEY_CODE_LTHUMB_RIGHT, false,_0);
                        key_listener.send_key_event(KEY_CODE_LTHUMB_DOWN, false,_0);
                        return;
                    }
                    if (pressed_x < 0) {
                        key_listener.send_key_event(KEY_CODE_LTHUMB_LEFT, true,(short) (pressed_x*32768.0f));
                        key_listener.send_key_event(KEY_CODE_LTHUMB_RIGHT, false,_0);
                    }
                    else {
                        key_listener.send_key_event(KEY_CODE_LTHUMB_LEFT, false,_0);
                        key_listener.send_key_event(KEY_CODE_LTHUMB_RIGHT, true,(short) (pressed_x*32767.0f));
                    }
                    //VirtualControl.Joystick 左上角为-1.0,-1.0
                    //X360左下角为 -32768,-32768
                    if (pressed_y > 0) {
                        key_listener.send_key_event(KEY_CODE_LTHUMB_UP, true,(short)(-pressed_y*32768.0f));
                        key_listener.send_key_event(KEY_CODE_LTHUMB_DOWN, false,_0);
                    }
                    else {
                        key_listener.send_key_event(KEY_CODE_LTHUMB_UP, false,_0);
                        key_listener.send_key_event(KEY_CODE_LTHUMB_DOWN, true,(short) (-pressed_y*32767.0f));
                    }
                }
                //right_joystick
                else{
                    if(!pressed){
                        key_listener.send_key_event(KEY_CODE_RTHUMB_LEFT, false,_0);
                        key_listener.send_key_event(KEY_CODE_RTHUMB_UP, false, _0);
                        key_listener.send_key_event(KEY_CODE_RTHUMB_RIGHT, false, _0);
                        key_listener.send_key_event(KEY_CODE_RTHUMB_DOWN, false, _0);
                        return;
                    }
                    if (pressed_x <0) {
                        key_listener.send_key_event(KEY_CODE_RTHUMB_LEFT, true, (short) (pressed_x*32768.0f));
                        key_listener.send_key_event(KEY_CODE_RTHUMB_RIGHT, false, _0);
                    }
                    else {
                        key_listener.send_key_event(KEY_CODE_RTHUMB_LEFT, false, _0);
                        key_listener.send_key_event(KEY_CODE_RTHUMB_RIGHT, true, (short) (pressed_x*32767.0f));
                    }
                    //VirtualControl.Joystick 左上角为-1.0,-1.0
                    //X360左下角为 -32768,-32768
                    if (pressed_y > 0) {
                        key_listener.send_key_event(KEY_CODE_RTHUMB_UP, true, (short) (-pressed_y*32768.0f));
                        key_listener.send_key_event(KEY_CODE_RTHUMB_DOWN, false, _0);
                    }
                    else {
                        key_listener.send_key_event(KEY_CODE_RTHUMB_UP, false, _0);
                        key_listener.send_key_event(KEY_CODE_RTHUMB_DOWN, true, (short) (-pressed_y*32767.0f));
                    }
                }
            }
        }

        @Override
        public String get_name() {
            return name;
        }

        @Override
        public double get_ratio() {
            return ratio;
        }

        @Override
        public int get_x() {
            return position_x;
        }

        @Override
        public int get_y() {
            return position_y;
        }

        @Override
        public double get_scale() {
            return scale;
        }

        @Override
        public double get_opacity() {
            return opacity;
        }

        @Override
        public void set_x(int x) {
            this.position_x=x;
        }

        @Override
        public void set_y(int y) {
            this.position_y=y;
        }

        @Override
        public void set_gamepad_event_listener(GamepadEventListener listener) {
            this.key_listener = listener;
        }

        @Override
        public void set_scale(double scale) {
            this.scale = scale;
            setup_bitmap();
            setup_rect();
        }

        @Override
        public void set_opacity(double opacity) {
            this.opacity = opacity;
            paint.setAlpha((int) (opacity * 255));
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawBitmap(large_bitmap, position_x, position_y, paint);
            final int center_x = (int) position_x + large_bitmap.getWidth() / 2;
            final int center_y = (int) position_y + large_bitmap.getHeight() / 2;
            if (pressed) {
                canvas.drawBitmap(pressed_bitmap
                        , (int)(center_x + pressed_x * bitmap.getWidth() - bitmap.getWidth() / 2)
                        , (int)(center_y + pressed_y * bitmap.getHeight() - bitmap.getHeight() / 2)
                        , paint);
            } else {
                canvas.drawBitmap(bitmap, center_x - bitmap.getWidth() / 2, center_y - bitmap.getHeight() / 2, paint);
            }
        }

        @Override
        public void on_touch(MotionEvent event,int left_joystick_pointer_id,int right_joystick_pointer_id) {
            int action = event.getActionMasked();
            boolean invalidate = false;

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    int pointerIndex = event.getActionIndex();
                    if (!pressed && rect.contains((int) event.getX(pointerIndex), (int) event.getY(pointerIndex))) {
                        pressed = true;
                        joystick_pointer_id = event.getPointerId(pointerIndex);
                        invalidate = true;
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (pressed) {
                        for (int i = 0; i < event.getPointerCount(); i++) {
                            if (event.getPointerId(i) == joystick_pointer_id) {
                                final int center_x = (int) position_x + large_bitmap.getWidth() / 2;
                                final int center_y = (int) position_y + large_bitmap.getHeight() / 2;
                                double offset_x = event.getX(i) - center_x;
                                double offset_y = event.getY(i) - center_y;

                                pressed_x = offset_x / (large_bitmap.getWidth() / 2);
                                pressed_y = offset_y / (large_bitmap.getHeight() / 2);

                                double distance = (double) Math.sqrt(pressed_x * pressed_x + pressed_y * pressed_y);
                                if (distance > 1.0f) {
                                    pressed_x /= distance;
                                    pressed_y /= distance;
                                }

                                pressed_x = Math.min(Math.max(pressed_x, -1.0f), 1.0f);
                                pressed_y = Math.min(Math.max(pressed_y, -1.0f), 1.0f);

                                invalidate = true;
                                break;
                            }
                        }
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    pointerIndex = event.getActionIndex();
                    if (pressed && joystick_pointer_id == event.getPointerId(pointerIndex)) {
                        pressed = false;
                        joystick_pointer_id = -1;
                        pressed_x = 0.0f;
                        pressed_y = 0.0f;
                        invalidate = true;
                    }
                    break;
            }

            if (invalidate) {
                send_key_event();
                invalidate();
            }
        }
    }


    public VirtualControl(Context context) {
        this(context,null);
    }
    public VirtualControl(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        requestFocus();
        if(getContext().getSharedPreferences("VirtualControl",Context.MODE_PRIVATE).getBoolean("disable",false))
            return;
        load_config(Application.get_virtual_control_config_file());

        last_time=System.currentTimeMillis();
        show=true;

        setOnTouchListener(this);
        for(Component component:components){
            component.set_gamepad_event_listener(key_listener);
        }

        hide_timer = new Timer();
        hide_timer.schedule(new TimerTask() {

            void task(){
                if(System.currentTimeMillis()-last_time>5*1000&&show){
                    show=false;
                    invalidate();
                }
            }
            @Override
            public void run() {
                task();
            }
        }, 1000,1000);
    }

    private VirtualControl(Context context, android.util.AttributeSet attrs,boolean unused) {
        super(context, attrs);
        setWillNotDraw(false);
        requestFocus();

        load_config(Application.get_virtual_control_config_file());

        last_time=System.currentTimeMillis();
        show=true;
    }
    static VirtualControl Edit(Context context){
        return new VirtualControl(context,null,true);
    }

    JSONObject default_config(){
        try {
            JSONObject config = new JSONObject();
            Context context = getContext();
            double ratio;
            int x, y;

            ratio = 0.35f;
            x = offset_x(context, 0.04f);
            y = offset_y(context, 0.58f);

            config.put("joystick_l", new JSONObject().put("x", x).put("y", y).put("ratio", ratio).put("scale", 1.0f).put("opacity", 1.0f));

            x = offset_x(context, 0.96f - ratio * offset_ratio(context));
            y = offset_y(context, 0.2f);
            config.put("joystick_r", new JSONObject().put("x", x).put("y", y).put("ratio", ratio).put("scale", 1.0f).put("opacity", 1.0f));

            //DPAD
            ratio = 0.35f;
            x = offset_x(context, 0.02f);
            y = offset_y(context, 0.2f);
            config.put("dpad", new JSONObject().put("x", x).put("y", y).put("ratio", ratio).put("scale", 1.0f).put("opacity", 1.0f));

            //BUTTONS
            //ABXY
            ratio = 0.13f;
            x = offset_x(context, 0.98f - ratio * offset_ratio(context) * 2);
            y = offset_y(context, 0.98f - ratio * 3);
            config.put("y", new JSONObject().put("x", x).put("y", y).put("ratio", ratio).put("scale", 1.0f).put("opacity", 1.0f));
            x = offset_x(context, 0.98f - ratio * offset_ratio(context) * 3);
            y = offset_y(context, 0.98f - ratio * 2);
            config.put("x", new JSONObject().put("x", x).put("y", y).put("ratio", ratio).put("scale", 1.0f).put("opacity", 1.0f));
            x = offset_x(context, 0.98f - ratio * offset_ratio(context));
            y = offset_y(context, 0.98f - ratio * 2);
            config.put("b", new JSONObject().put("x", x).put("y", y).put("ratio", ratio).put("scale", 1.0f).put("opacity", 1.0f));
            x = offset_x(context, 0.98f - ratio * offset_ratio(context) * 2);
            y = offset_y(context, 0.98f - ratio * 1);
            config.put("a", new JSONObject().put("x", x).put("y", y).put("ratio", ratio).put("scale", 1.0f).put("opacity", 1.0f));


            //BUTTONS
            //BACK/START
            ratio = 0.13f;

            x = offset_x(context, 0.49f - ratio * offset_ratio(context));
            y = offset_y(context, 0.98f - ratio);
            config.put("back", new JSONObject().put("x", x).put("y", y).put("ratio", ratio).put("scale", 1.0f).put("opacity", 1.0f));

            x = offset_x(context, 0.51f);
            y = offset_y(context, 0.98f - ratio);
            config.put("start", new JSONObject().put("x", x).put("y", y).put("ratio", ratio).put("scale", 1.0f).put("opacity", 1.0f));


            //BUTTONS
            //other

            ratio = 0.13f;

            x = offset_x(context, 0.02f);
            y = offset_y(context, 0.02f);
            config.put("shoulder_l", new JSONObject().put("x", x).put("y", y).put("ratio", ratio).put("scale", 1.0f).put("opacity", 1.0f));
            x = offset_x(context, 0.98f - ratio * offset_ratio(context));
            y = offset_y(context, 0.02f);
            config.put("shoulder_r", new JSONObject().put("x", x).put("y", y).put("ratio", ratio).put("scale", 1.0f).put("opacity", 1.0f));
            x = offset_x(context, 0.04f + ratio * offset_ratio(context));
            y = offset_y(context, 0.02f);
            config.put("thumb_press_l", new JSONObject().put("x", x).put("y", y).put("ratio", ratio).put("scale", 1.0f).put("opacity", 1.0f));
            x = offset_x(context, 0.96f - ratio * offset_ratio(context) * 2);
            y = offset_y(context, 0.02f);
            config.put("thumb_press_r", new JSONObject().put("x", x).put("y", y).put("ratio", ratio).put("scale", 1.0f).put("opacity", 1.0f));
            x = offset_x(context, 0.06f + ratio * offset_ratio(context) * 2);
            y = offset_y(context, 0.02f);
            config.put("trigger_l", new JSONObject().put("x", x).put("y", y).put("ratio", ratio).put("scale", 1.0f).put("opacity", 1.0f));
            x = offset_x(context, 0.94f - ratio * offset_ratio(context) * 3);
            y = offset_y(context, 0.02f);
            config.put("trigger_r", new JSONObject().put("x", x).put("y", y).put("ratio", ratio).put("scale", 1.0f).put("opacity", 1.0f));

            return config;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void load_config(File config_file){
        if(config_file.exists()){
            try{
                load_config(new JSONObject(Utils.load_string(config_file)));
            }catch(Exception e){
                load_config(default_config());
            }
        }else{
            load_config(default_config());
        }
    }

    void load_config(JSONObject  config){
        try{
            Context context=this.getContext();
            components.clear();

            JSONObject joystick_l=config.getJSONObject("joystick_l");
            components.add(new Joystick("joystick_l",context,R.drawable.joystick,R.drawable.joystick_pressed,R.drawable.joystick_range,joystick_l.getDouble("ratio"),joystick_l.getInt("x"),joystick_l.getInt("y"),true));
            JSONObject joystick_r=config.getJSONObject("joystick_r");
            components.add(new Joystick("joystick_r",context,R.drawable.joystick,R.drawable.joystick_pressed,R.drawable.joystick_range,joystick_r.getDouble("ratio"),joystick_r.getInt("x"),joystick_r.getInt("y"),false));

            JSONObject dpad=config.getJSONObject("dpad");
            components.add(new Dpad("dpad",context,R.drawable.dpad_idle,R.drawable.dpad_left,R.drawable.dpad_left_up,dpad.getDouble("ratio"),dpad.getInt("x"),dpad.getInt("y")));

            JSONObject a=config.getJSONObject("a");
            components.add(new Button("a",context,R.drawable.a,a.getDouble("ratio"),a.getInt("x"),a.getInt("y"),VirtualControl.KEY_CODE_A));
            JSONObject b=config.getJSONObject("b");
            components.add(new Button("b",context,R.drawable.b,b.getDouble("ratio"),b.getInt("x"),b.getInt("y"),VirtualControl.KEY_CODE_B));
            JSONObject x=config.getJSONObject("x");
            components.add(new Button("x",context,R.drawable.x,x.getDouble("ratio"),x.getInt("x"),x.getInt("y"),VirtualControl.KEY_CODE_X));
            JSONObject y=config.getJSONObject("y");
            components.add(new Button("y",context,R.drawable.y,y.getDouble("ratio"),y.getInt("x"),y.getInt("y"),VirtualControl.KEY_CODE_Y));
            JSONObject back=config.getJSONObject("back");
            components.add(new Button("back",context,R.drawable.back,back.getDouble("ratio"),back.getInt("x"),back.getInt("y"),VirtualControl.KEY_CODE_BACK));
            JSONObject start=config.getJSONObject("start");
            components.add(new Button("start",context,R.drawable.start,start.getDouble("ratio"),start.getInt("x"),start.getInt("y"),VirtualControl.KEY_CODE_START));
            JSONObject shoulder_l=config.getJSONObject("shoulder_l");
            components.add(new Button("shoulder_l",context,R.drawable.lsb,shoulder_l.getDouble("ratio"),shoulder_l.getInt("x"),shoulder_l.getInt("y"),VirtualControl.KEY_CODE_SHOULDER_L));
            JSONObject shoulder_r=config.getJSONObject("shoulder_r");
            components.add(new Button("shoulder_r",context,R.drawable.rsb,shoulder_r.getDouble("ratio"),shoulder_r.getInt("x"),shoulder_r.getInt("y"),VirtualControl.KEY_CODE_SHOULDER_R));
            JSONObject thumb_press_l=config.getJSONObject("thumb_press_l");
            components.add(new Button("thumb_press_l",context,R.drawable.lb,thumb_press_l.getDouble("ratio"),thumb_press_l.getInt("x"),thumb_press_l.getInt("y"),VirtualControl.KEY_CODE_THUMB_PRESS_L));
            JSONObject thumb_press_r=config.getJSONObject("thumb_press_r");
            components.add(new Button("thumb_press_r",context,R.drawable.rb,thumb_press_r.getDouble("ratio"),thumb_press_r.getInt("x"),thumb_press_r.getInt("y"),VirtualControl.KEY_CODE_THUMB_PRESS_R));
            JSONObject trigger_l=config.getJSONObject("trigger_l");
            components.add(new Button("trigger_l",context,R.drawable.lt,trigger_l.getDouble("ratio"),trigger_l.getInt("x"),trigger_l.getInt("y"),VirtualControl.KEY_CODE_TRIGGER_L));
            JSONObject trigger_r=config.getJSONObject("trigger_r");
            components.add(new Button("trigger_r",context,R.drawable.rt,trigger_r.getDouble("ratio"),trigger_r.getInt("x"),trigger_r.getInt("y"),VirtualControl.KEY_CODE_TRIGGER_R));

            find_component("joystick_l").set_scale(joystick_l.getDouble("scale"));
            find_component("joystick_r").set_scale(joystick_r.getDouble("scale"));
            find_component("dpad").set_scale(dpad.getDouble("scale"));
            find_component("a").set_scale(a.getDouble("scale"));
            find_component("b").set_scale(b.getDouble("scale"));
            find_component("x").set_scale(x.getDouble("scale"));
            find_component("y").set_scale(y.getDouble("scale"));
            find_component("back").set_scale(back.getDouble("scale"));
            find_component("start").set_scale(start.getDouble("scale"));
            find_component("shoulder_l").set_scale(shoulder_l.getDouble("scale"));
            find_component("shoulder_r").set_scale(shoulder_r.getDouble("scale"));
            find_component("thumb_press_l").set_scale(thumb_press_l.getDouble("scale"));
            find_component("thumb_press_r").set_scale(thumb_press_r.getDouble("scale"));
            find_component("trigger_l").set_scale(trigger_l.getDouble("scale"));
            find_component("trigger_r").set_scale(trigger_r.getDouble("scale"));
        }
        catch(Exception e){
            load_config(default_config());
        }
    }

    JSONObject save_config(){
        try {
            JSONObject config=new JSONObject();
            for(Component component:components){
                config.put(component.get_name(),new JSONObject().put("x",component.get_x()).put("y",component.get_y()).put("ratio",component.get_ratio()).put("scale",component.get_scale()).put("opacity",component.get_opacity()));
            }
            return config;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    void save_config(File config_file){
        try {
            Utils.save_string(config_file, save_config().toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    Component find_component(String name){
        for(Component component:components){
            if(component.get_name().equals(name)){
                return component;
            }
        }
        return null;
    }

    Component find_component_by_touch_point(int x,int y){
        for(Component component:components){
            final int l=component.get_x();
            final int u=component.get_y();
            final int r=component.get_x()+(int)(get_width_height(getContext(),component.get_ratio())*component.get_scale());
            final int d=component.get_y()+(int)(get_width_height(getContext(),component.get_ratio())*component.get_scale());
            if(x>=l&&x<=r&&y>=u&&y<=d){
                return component;
            }
        }
        return null;
    }

    void set_component_position(Component component,int center_x,int center_y){
        int w_h=(int)(get_width_height(getContext(),component.get_ratio())*component.get_scale());
        component.set_x(center_x-w_h/2);
        component.set_y(center_y-w_h/2);
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if(!show) return;
        for(Component component:components){
            component.draw(canvas);
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        show=true;
        if(!components.get(0).get_name().startsWith("joystick")
                || !components.get(1).get_name().startsWith("joystick"))
            throw new RuntimeException("joystick_l and joystick_r must be the first two components");
        int left_joystick_id=((Joystick)components.get(0)).joystick_pointer_id;
        int right_joystick_id=((Joystick)components.get(1)).joystick_pointer_id;
        for(Component component:components){
            component.on_touch(event,left_joystick_id,right_joystick_id);
        }
        last_time=System.currentTimeMillis();
        return true;
    }

    GamepadEventListener key_listener=new GamepadEventListener() {
        @Override
        public void send_key_event(int key_code,boolean pressed, short value) {
            Emulator.get.key_event(key_code,pressed, value);
        }
    };
    List<Component> components=new ArrayList<>();
    long last_time=0;
    boolean show;
    Timer hide_timer;

    @Override
    protected void onDetachedFromWindow() {
        if (hide_timer != null) {
            hide_timer.cancel();
            hide_timer = null;
        }
        super.onDetachedFromWindow();
    }
}
