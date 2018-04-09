package company.airider.gcs.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.text.Html;
import android.text.InputFilter;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.getbase.floatingactionbutton.FloatingActionButton;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.ButtCap;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import company.airider.gcs.DeviceData;
import company.airider.gcs.test.sash0k.bluetooth_terminal.R;
import company.airider.gcs.Utils;
import company.airider.gcs.bluetooth.DeviceConnector;
import company.airider.gcs.bluetooth.DeviceListActivity;

import static company.airider.gcs.test.sash0k.bluetooth_terminal.R.id.map;
import static company.airider.gcs.test.sash0k.bluetooth_terminal.R.id.seekbarvalueCP;
import static company.airider.gcs.test.sash0k.bluetooth_terminal.R.id.seekbarvaluealt;
import static company.airider.gcs.test.sash0k.bluetooth_terminal.R.id.seekbarvaluech;
import static company.airider.gcs.test.sash0k.bluetooth_terminal.R.id.seekbarvalueheading;
import static company.airider.gcs.test.sash0k.bluetooth_terminal.R.id.seekbarvaluespeed;


public class    DeviceControlActivity extends BaseFragmentActivity implements View.OnClickListener, OnMapReadyCallback {
    private static final String DEVICE_NAME = "DEVICE_NAME";
    private static final String LOG = "LOG";

    private static final String CRC_OK = "#FFFF00";
    private static final String CRC_BAD = "#FF0000";

    private static final SimpleDateFormat timeformat = new SimpleDateFormat("HH:mm:ss.SSS");

    private static String MSG_NOT_CONNECTED;
    private static String MSG_CONNECTING;
    private static String MSG_CONNECTED;

    private static DeviceConnector connector;
    private static BluetoothResponseHandler mHandler;

    private StringBuilder logHtml;
    private TextView logTextView;
    private EditText commandEditText;

    // Настройки приложения
    private boolean hexMode, checkSum, needClean;
    private boolean show_timings, show_direction;
    private String command_ending;
    private String deviceName;
    public GoogleMap mMap;

    public int ValueAlt=50;
    public int ValueSpeed=5;
    public int ValueHeading=0;
    public int ValueCP=0;
    public int ValueCH=0;
    public String AutoPilot = "$RCS+Auto Pilot=";
    public String FCSDL = "$FCS+DL";
    public String TEST = "TEST";
    public String TESTSTOP = "TESTSTOP";

    //private static Lock lock = new ReentrantLock();
    public String UAV[];
    public Double DL[][] = new Double[5000][7];
    public Double touchpoint[][] = new Double[5000][7];

    public float UAV_Vehicle_Batt;
    public ProgressBar progressBar;
    public Marker PlaneLatLng;
    public Marker PrePlaneLatLng;
    public Marker TestLatLng;
    public Marker PreTestLatLng;

    public int PB=5;
    public Polygon Ply;
    public Marker Plycenter;
    List<LatLng> OutSide = new ArrayList<>() ;
    public Polyline PrePlanePolyline, PlanePolyline,allline;
    public Marker linemarkers;
    public LatLng PolylinenextPoint;
    public double testpointlng = 121.4202272232666, pretestpointlng = 121.4202272232666;
    public double testpointlat = 25.04359753643703, pretestpointlat = 25.04359753643703;
    int k = 0,nextpoint = 0;
    int[] key = {0};

    public final List<LatLng> points = new ArrayList<>();
    public int j=0;
    public Polygon polygonpre = null;
    int autoclac = 0;

    public int CameraOperation = 0;
    public int testTV = 0;
    public double degree=0;
    public double spacing=5;
    boolean ploy_switch = true;
    public String[] Read_Data_Array;

    Boolean Polyline_onoff_switch =true;

    int flag=0;
    int what=0;
    int god=0;


    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PreferenceManager.setDefaultValues(this, R.xml.settings_activity, false);


        // 讓手機螢幕保持恆立模式
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        //設定隱藏標題
        getActionBar().hide();
        //設定隱藏狀態
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        //設定自動彈出鍵盤
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        /**Bluetooth****************************************************************************/
        if (mHandler == null) mHandler = new BluetoothResponseHandler(this);
        else mHandler.setTarget(this);

        MSG_NOT_CONNECTED = getString(R.string.msg_not_connected);
        MSG_CONNECTING = getString(R.string.msg_connecting);
        MSG_CONNECTED = getString(R.string.msg_connected);

        setContentView(R.layout.activity_terminal);
        if (isConnected() && (savedInstanceState != null)) {
            setDeviceName(savedInstanceState.getString(DEVICE_NAME));
        } else getActionBar().setSubtitle(MSG_NOT_CONNECTED);

        this.logHtml = new StringBuilder();
        if (savedInstanceState != null) this.logHtml.append(savedInstanceState.getString(LOG));

        this.logTextView = (TextView) findViewById(R.id.log_textview);
        this.logTextView.setMovementMethod(new ScrollingMovementMethod());
        this.logTextView.setText(Html.fromHtml(logHtml.toString()));

        this.commandEditText = (EditText) findViewById(R.id.command_edittext);

        // soft-keyboard send button
        this.commandEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendCommand(null);
                    return true;
                }
                return false;
            }
        });
        // hardware Enter button
        this.commandEditText.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_ENTER:
                            sendCommand(null);
                            return true;
                        default:
                            break;
                    }
                }
                return false;
            }
        });

        /**Bluetooth****************************************************************************/
        /**GoogleMap****************************************************************************/
        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(map);
        mapFragment.getMapAsync(this);
        /**GoogleMap****************************************************************************/
        /**點擊按鈕事件****************************************************************************/

        FloatingActionButton menu_enable = (FloatingActionButton) findViewById(R.id.menu_enable);
        FloatingActionButton menu_clear = (FloatingActionButton) findViewById(R.id.menu_clear);
        FloatingActionButton menu_send = (FloatingActionButton) findViewById(R.id.menu_send);
        FloatingActionButton menu_setting = (FloatingActionButton) findViewById(R.id.menu_setting);
        FloatingActionButton about = (FloatingActionButton) findViewById(R.id.about);

        menu_enable.setOnClickListener(this);
        menu_clear.setOnClickListener(this);
        menu_send.setOnClickListener(this);
        menu_setting.setOnClickListener(this);
        about.setOnClickListener(this);

        /**點擊按鈕事件****************************************************************************/
        /**設定高度事件****************************************************************************/
        SeekBar seekBarAlt = (SeekBar) findViewById(R.id.seekBarAlt);
        final EditText seekBarValueAlt = (EditText) findViewById(R.id.seekbarvaluealt);
        seekBarAlt.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                seekBarValueAlt.setText(String.valueOf(progress-50));
                ValueAlt = progress-50;

            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        /**設定高度事件****************************************************************************/
        /**設定導航速度事件****************************************************************************/
        SeekBar seekBarSpeed = (SeekBar) findViewById(R.id.seekBarSpeed);
        final EditText seekBarValueSpeed = (EditText) findViewById(R.id.seekbarvaluespeed);
        seekBarSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                seekBarValueSpeed.setText(String.valueOf(progress));
                ValueSpeed = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        /**設定導航速度事件****************************************************************************/
        /**設定導航機身轉向事件****************************************************************************/
        SeekBar seekBarHeading = (SeekBar) findViewById(R.id.seekBarHeading);
        final EditText seekBarValueHeading = (EditText) findViewById(R.id.seekbarvalueheading);
        seekBarHeading.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                seekBarValueHeading.setText(String.valueOf(progress-180));
                ValueHeading = progress-180;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        /**設定導航機身轉向事件****************************************************************************/
        /**設定導航雲台pitch事件****************************************************************************/
        SeekBar seekBarCP = (SeekBar) findViewById(R.id.seekBarCP);

        final EditText seekBarValueCP = (EditText) findViewById(R.id.seekbarvalueCP);
        seekBarCP.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                seekBarValueCP.setText(String.valueOf(progress));
                ValueCP = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        /**設定導航雲台pitch事件****************************************************************************/
        /**設定導航雲台轉向事件****************************************************************************/
        SeekBar seekBarCH = (SeekBar) findViewById(R.id.seekBarCH);
        final EditText seekBarValueCH = (EditText) findViewById(R.id.seekbarvaluech);
        seekBarCH.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                seekBarValueCH.setText(String.valueOf(progress - 90));
                ValueCH = progress - 90;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        /**設定導航雲台轉向事件****************************************************************************/
        /**拖曳標記事件****************************************************************************/
        SeekBar seekBarSpacing = (SeekBar)findViewById(R.id.seekBarSpacing);

        final EditText TextViewSpacing1= (EditText)findViewById(R.id.TextViewSpacing);
        seekBarSpacing.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                PB=progress;
                TextViewSpacing1.setText(String.valueOf(progress));
                spacing = Double.valueOf(TextViewSpacing1.getText().toString());
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        /**拖曳標記事件****************************************************************************/
        SeekBar angle = (SeekBar)findViewById(R.id.seekBarAngle);

        final EditText textangle= (EditText)findViewById(R.id.textangle);

        final List<LatLng> trans = new ArrayList<>() ;
        angle.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                //trans.clear();

                //for(int round = 0 ;round <Ply.getPoints().size()-1;round ++){
                //    trans.add(transform(Ply.getPoints().get(round).latitude,Ply.getPoints().get(round).longitude,Plycenter.getPosition().latitude,Plycenter.getPosition().longitude,progress,1,1));}
                /**畫最大範圍*******************************************************************************/
                //Ply.setPoints(trans);
                /**畫中心點*******************************************************************************/


                textangle.setText(String.valueOf(progress));
                degree = Double.valueOf(textangle.getText().toString());
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        /**拖曳標記事件****************************************************************************/
        progressBar = (ProgressBar) findViewById(R.id.progressBar3);


    }

    Handler handler = new Handler();
    Runnable updateThread = new Runnable() {
        public void run() {
            sendCommand(null);
            handler.postDelayed(updateThread, 1000);
        }
    };
//    Runnable Cpature = new Runnable() {
//        public void run() {
//
//            if(CameraOperationtime>=4){CameraOperationtime=0;}
//            CameraOperationtime+=1;
//            handler.postDelayed(Cpature, 1000);
//        }
//    };
    Runnable UpdateRate = new Runnable() {
        public void run() {
                flymode();
                handler.postDelayed(UpdateRate, 50);
        }
    };
    Runnable test = new Runnable() {
        public void run() {
            //Test();
            handler.postDelayed(test, 1000);
        }
    };

    @SuppressLint("ResourceType")
    @Override
    public void onMapReady(GoogleMap googleMap)
    {

        final EditText seekBarValueAlt = (EditText) findViewById(R.id.seekbarvaluealt);
        final EditText seekBarValueSpeed = (EditText) findViewById(R.id.seekbarvaluespeed);
        final EditText seekBarValueHeading = (EditText) findViewById(R.id.seekbarvalueheading);
        final EditText seekBarValueCP = (EditText) findViewById(R.id.seekbarvalueCP);
        final EditText seekBarValueCH = (EditText) findViewById(R.id.seekbarvaluech);
        final EditText textangle= (EditText)findViewById(R.id.textangle);
        final EditText TextViewSpacing1= (EditText)findViewById(R.id.TextViewSpacing);

        mMap = googleMap;
        mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID); // 一般地圖
        //mMap.getUiSettings().setZoomControlsEnabled(true);// 顯示縮放圖示
        mMap.getUiSettings().setCompassEnabled(true); // 顯示指南針
        mMap.getUiSettings().setMyLocationButtonEnabled(true); //顯示我的位置按鈕
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;}
        mMap.setMyLocationEnabled(true); //顯示我的位置
        LocationManager lms = (LocationManager) getSystemService(LOCATION_SERVICE);
        Location location = lms.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        lms.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        Double new_longitude = location.getLongitude();
        Double new_latitude = location.getLatitude();
        final LatLng place = new LatLng(new_latitude,new_longitude);
        moveMap(place);




        /**點擊地圖事件****************************************************************************/
        Switch poly = (Switch)findViewById(R.id.poly1);
        //poly.setChecked(true);
        poly.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                ploy_switch = isChecked;
                if(ploy_switch==false)
                {
                    mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
                        @Override
                        public void onMapClick(final LatLng point) {
                            j = j + 1;
                            DecimalFormat df = new DecimalFormat("####.0000000");
                            touchpoint[j][0] = Double.parseDouble(df.format(point.longitude));
                            touchpoint[j][1] = Double.parseDouble(df.format(point.latitude));
                            touchpoint[j][2] = Double.valueOf(String.valueOf(seekBarValueAlt.getText())) ;
                            touchpoint[j][3] = Double.valueOf(String.valueOf(seekBarValueHeading.getText()));
                            touchpoint[j][4] = Double.valueOf(String.valueOf(seekBarValueSpeed.getText()));
                            touchpoint[j][5] = Double.valueOf(String.valueOf(seekBarValueCP.getText()));
                            touchpoint[j][6] = Double.valueOf(String.valueOf(seekBarValueCH.getText()));

                            Marker singel_line = mMap.addMarker(new MarkerOptions()
                                    .position(new LatLng(touchpoint[j][1], touchpoint[j][0]))
                                    .draggable(true)
                            );
                            mMap.addCircle(new CircleOptions()
                                    .center(new LatLng(touchpoint[j][1], touchpoint[j][0]))
                                    .radius(2)
                                    .strokeWidth(2)
                                    .strokeColor(Color.YELLOW)
                                    .fillColor(Color.argb(153, 239, 130, 0))
                            );
                            if (j > 1) {
                                LatLng currentPoint = new LatLng(touchpoint[j - 1][1], touchpoint[j - 1][0]);
                                LatLng nextPoint = new LatLng(touchpoint[j][1], touchpoint[j][0]);
                                Polyline line=mMap.addPolyline(new PolylineOptions()
                                        .add(currentPoint, nextPoint)
                                        .width(5)
                                        .endCap(new ButtCap())
                                        .startCap(new RoundCap())

                                );
                                line.setEndCap(new ButtCap());

                                //TextView alldistance = (TextView) findViewById(R.id.AllDistance);
                                //Double D = getDistance(touchpoint[j][1], touchpoint[j][0], touchpoint[j - 1][1], touchpoint[j - 1][0]);
                                //alldistance.setText(D.toString() + "m");
                                //line.setTag(D.toString() + "m");

                            }
                            OutSide.add(singel_line.getPosition());
                        }
                    });

                }
                if (ploy_switch ==true)
                {
                    mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
                        @Override
                        public void onMapClick(final LatLng point) {
                            j = j + 1;
                            DecimalFormat df = new DecimalFormat("####.0000000");
                            touchpoint[j][0] = Double.parseDouble(df.format(point.longitude));
                            touchpoint[j][1] = Double.parseDouble(df.format(point.latitude));
                            touchpoint[j][2] = Double.valueOf(String.valueOf(seekBarValueAlt.getText())) ;
                            touchpoint[j][3] = Double.valueOf(String.valueOf(seekBarValueHeading.getText()));
                            touchpoint[j][4] = Double.valueOf(String.valueOf(seekBarValueSpeed.getText()));
                            touchpoint[j][5] = Double.valueOf(String.valueOf(seekBarValueCP.getText()));
                            touchpoint[j][6] = Double.valueOf(String.valueOf(seekBarValueCH.getText()));
                            mMap.addMarker(new MarkerOptions()
                                    .position(new LatLng(touchpoint[j][1], touchpoint[j][0]))
                                    .draggable(true)
                            );

                            if(j > 3){ polygonpre.remove();}
                            points.add(new LatLng(touchpoint[j][1], touchpoint[j][0]));
                            if (j > 2) {

                                polygonpre = mMap.addPolygon(new PolygonOptions()
                                        .addAll(points)
                                        .strokeWidth(3)
                                        .fillColor(Color.argb(153, 239, 130, 0))
                                );

                            }
                        }
                    });
                }
            }
        });

        Button clean = (Button)findViewById(R.id.cleanall);
        clean.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(mMap!=null){mMap.clear();}
                if(points!=null){points.clear();}
                if(polygonpre!=null){polygonpre.remove();}
                if(linemarkers!=null){linemarkers.remove();}
                if(OutSide!=null){OutSide.clear();}
                if(Ply!=null){Ply.remove();}
                j=0;
            }
        });


        Button DLine =(Button)findViewById(R.id.Dline);
        DLine.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawline();
        }
        });
        Button file_writer = (Button)findViewById(R.id.file_writer);
        file_writer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try{
                    FileWriter fw = new FileWriter("/storage/emulated/0/qpython/inputpoint.txt", false);
                    BufferedWriter bw = new BufferedWriter(fw); //將BufferedWeiter與FileWrite物件做連結
                    bw.write(String.valueOf(degree));
                    bw.newLine();
                    bw.write(String.valueOf(spacing));
                    bw.newLine();
                    bw.write(String.valueOf("0-1"));
                    bw.newLine();
                    for (int total = 0; total < points.size(); total++)
                    {
                        bw.write("("+ points.get(total).longitude + "," +points.get(total).latitude +")");
                        bw.newLine();
                    }
                    bw.close();
                }catch(IOException e){
                    e.printStackTrace();
                }
            }
        });
        Button file_read = (Button)findViewById(R.id.file_read);
        file_read.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try{
                    //建立FileReader物件，並設定讀取的檔案為SD卡中的output.txt檔案
                    FileReader fr = new FileReader("/storage/emulated/0/qpython/output.txt");
                    //將BufferedReader與FileReader做連結
                    BufferedReader br = new BufferedReader(fr);
                    //String readData = "";
                    String temp = br.readLine(); //readLine()讀取一整行
                    while (temp!=null)
                    {
                        //readData+=temp;

                        String[] lng =temp.replace("(","").replace(")","").split(",");
                        OutSide.add(new LatLng(Double.valueOf(lng[1]),Double.valueOf(lng[0])));
                        linemarkers = mMap.addMarker(new MarkerOptions()
                                .position(new LatLng(Double.valueOf(lng[1]),Double.valueOf(lng[0])))
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));
                        temp=br.readLine();
                        if(temp==null){break;}
                    }
                    for (int total = 1; total < OutSide.size(); total++) {
                        allline = mMap.addPolyline(new PolylineOptions()
                                .add(OutSide.get(total - 1), OutSide.get(total))
                                .width(8)
                                .color(Color.GREEN)
                        );
                    }
//                    Context context = getApplicationContext();
//                    int duration = Toast.LENGTH_LONG;
//                    Toast toast = Toast.makeText(context, readData, duration);
//                    toast.show();
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        });


        Button open_file = (Button)findViewById(R.id.open_file);
        open_file.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try{
                    //建立FileReader物件，並設定讀取的檔案為SD卡中的output.txt檔案
                    FileReader fr = new FileReader("/storage/emulated/0/qpython/onStop.txt");
                    //將BufferedReader與FileReader做連結
                    BufferedReader br = new BufferedReader(fr);
                    String readData = "";
                    String temp = br.readLine(); //readLine()讀取一整行
                    while (temp!=null){
                        readData+=temp+",";
                        temp=br.readLine();
                    }

                    Read_Data_Array=readData.split(",");
                    seekBarValueAlt.setText(Read_Data_Array[0]);
                    seekBarValueSpeed.setText(Read_Data_Array[1]);
                    seekBarValueHeading.setText(Read_Data_Array[2]);
                    seekBarValueCP.setText(Read_Data_Array[3]);
                    seekBarValueCH.setText(Read_Data_Array[4]);
                    textangle.setText(Read_Data_Array[5]);
                    TextViewSpacing1.setText(Read_Data_Array[6]);
                    if(Read_Data_Array.length>7)
                    {
                        for(int point_flag=7;point_flag<Read_Data_Array.length;point_flag=point_flag+2)
                        {
                            j=j+1;
                            points.add(new LatLng(Double.valueOf(Read_Data_Array[point_flag+1]),Double.valueOf(Read_Data_Array[point_flag]) ));
                            mMap.addMarker(new MarkerOptions()
                                    .position(new LatLng(Double.valueOf(Read_Data_Array[point_flag+1]),Double.valueOf(Read_Data_Array[point_flag])))
                                    .draggable(true)
                            );
                        }
                        if (j > 2 ) {

                            polygonpre = mMap.addPolygon(new PolygonOptions()
                                    .addAll(points)
                                    .strokeWidth(3)
                                    .fillColor(Color.argb(153, 239, 130, 0))
                            );

                        }


                    }

                    Context context = getApplicationContext();
                    int duration = Toast.LENGTH_LONG;
                    Toast toast = Toast.makeText(context, "讀取完成", duration);
                    toast.show();
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        });
        Button file_save = (Button)findViewById(R.id.file_save);
        file_save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try{
                    FileWriter fw = new FileWriter("/storage/emulated/0/qpython/onStop.txt", false);
                    BufferedWriter bw = new BufferedWriter(fw); //將BufferedWeiter與FileWrite物件做連結
                    bw.write(String.valueOf(ValueAlt));
                    bw.newLine();
                    bw.write(String.valueOf(ValueSpeed));
                    bw.newLine();
                    bw.write(String.valueOf(ValueHeading));
                    bw.newLine();
                    bw.write(String.valueOf(ValueCP));
                    bw.newLine();
                    bw.write(String.valueOf(ValueCH));
                    bw.newLine();
                    bw.write(String.valueOf(degree));
                    bw.newLine();
                    bw.write(String.valueOf(spacing));
                    bw.newLine();
                    if(points!=null)
                    {
                        for (int total = 0; total < points.size(); total++)
                        {
                            bw.write( points.get(total).longitude + "," +points.get(total).latitude );
                            bw.newLine();
                        }
                        bw.close();
                    }
                }catch(IOException e){
                    e.printStackTrace();
                }
            }
        });

        ImageButton sendbutton= (ImageButton) findViewById(R.id.sendbutton);
        sendbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                key[0]++;
                key[0] = key[0] % 2;
                if (key[0] == 1) {
                    new AlertDialog.Builder(DeviceControlActivity.this)
                            .setTitle("確定執行")//設定視窗標題
                            .setIcon(R.mipmap.ic_launcher)//設定對話視窗圖示
                            .setMessage("確定執行導航嗎?   按下'是的，請執行'即往第一點飛行")//設定顯示的文字
                            .setPositiveButton("是的，請執行",new DialogInterface.OnClickListener(){
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if(UAV==null)
                                    {
                                        key[0]=0;
                                        handler.removeCallbacks(updateThread);
                                        Toast toast = Toast.makeText(DeviceControlActivity.this,
                                                "藍芽尚未連線唷!!!", Toast.LENGTH_LONG);
                                        toast.show();
                                    }
                                    else if(UAV[10]=="1" || UAV[10]=="2")
                                    {
                                        key[0]=0;
                                        handler.removeCallbacks(updateThread);
                                        Toast toast = Toast.makeText(DeviceControlActivity.this,
                                                "搖桿回復中或搖桿未置中!!!", Toast.LENGTH_LONG);
                                        toast.show();
                                    }
                                    else if( OutSide.size()==0)
                                    {
                                        key[0]=0;
                                        handler.removeCallbacks(updateThread);
                                        Toast toast = Toast.makeText(DeviceControlActivity.this,
                                                "沒有規劃路徑點唷!!!", Toast.LENGTH_LONG);
                                        toast.show();
                                    }
                                    else {
                                        ImageButton sendbutton= (ImageButton) findViewById(R.id.sendbutton);
                                        sendbutton.setImageResource(R.drawable.missonstop);
                                        if(god==0)
                                        {
                                            DL[nextpoint][0]    /** Longitude*/ = Double.valueOf(999);
                                            DL[nextpoint][1]    /** Latitude*/ = Double.valueOf(99);
                                            DL[nextpoint][2]    /** Altitude*/ = Double.valueOf(9999);
                                            DL[nextpoint][3]    /** Heading*/ = Double.valueOf(999);
                                            DL[nextpoint][4]    /** Navi_Speed*/ = Double.valueOf(99);
                                            DL[nextpoint][5]    /** Camera_Pitch*/ = Double.valueOf(999);
                                            DL[nextpoint][6]    /** Camera_Heading*/ = Double.valueOf(999);
                                            CameraOperation     /**CameraOperation*/ = 0;   /**     0=none	1=Half	2=Shoot	3=Zin	4=Zout	5=Record    */
                                            handler.post(UpdateRate);
                                            god=1;
                                        }
                                        if(flag==0)
                                        {
                                            DL[nextpoint][0]    /** Longitude*/ = Double.valueOf(999);
                                            DL[nextpoint][1]    /** Latitude*/ = Double.valueOf(99);
                                            DL[nextpoint][2]    /** Altitude*/ = Double.valueOf(9999);
                                            DL[nextpoint][3]    /** Heading*/ = Double.valueOf(999);
                                            DL[nextpoint][4]    /** Navi_Speed*/ = Double.valueOf(99);
                                            DL[nextpoint][5]    /** Camera_Pitch*/ = Double.valueOf(999);
                                            DL[nextpoint][6]    /** Camera_Heading*/ = Double.valueOf(999);
                                            CameraOperation     /**CameraOperation*/ = 0;   /**     0=none	1=Half	2=Shoot	3=Zin	4=Zout	5=Record    */
                                            handler.post(updateThread);
                                            flag=1;
                                        }
//                                        finish();
                                    }
                                }
                            })//設定結束的子視窗
                            .show();//呈現對話視窗
                    //handler.post(gogogo);
                }
//                if (key[0] == 0) {
//                    ImageButton sendbutton= (ImageButton) findViewById(R.id.sendbutton);
//                    sendbutton.setImageResource(R.drawable.misson);
//                    handler.removeCallbacks(updateThread);
//                    handler.removeCallbacks(UpdateRate);
//                    god=0;
//                    flag=0;
//                    nextpoint=0;
//                    //handler.removeCallbacks(gogogo);
//                }
            }
        });
        /**拖曳標記事件****************************************************************************/
        mMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(Marker marker) {
            }
            @Override
            public void onMarkerDrag(Marker marker) {
            }
            @Override
            public void onMarkerDragEnd(Marker marker) {
            }
        });
        /**點擊標記事件****************************************************************************/
        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker arg0) {
                return true;
            }
        });
                /**點擊標記事件****************************************************************************/
                }

public void moveMap(LatLng place)
        {
        // 建立地圖攝影機的位置物件
        CameraPosition cameraPosition =
        new CameraPosition.Builder()
        .target(place)
        .zoom(19)
        .build();
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        }
    @Override
protected void onStop() {
        super.onStop();
        try{
            FileWriter fw = new FileWriter("/storage/emulated/0/qpython/onStop.txt", false);
            BufferedWriter bw = new BufferedWriter(fw); //將BufferedWeiter與FileWrite物件做連結
            bw.write(String.valueOf(ValueAlt));
            bw.newLine();
            bw.write(String.valueOf(ValueSpeed));
            bw.newLine();
            bw.write(String.valueOf(ValueHeading));
            bw.newLine();
            bw.write(String.valueOf(ValueCP));
            bw.newLine();
            bw.write(String.valueOf(ValueCH));
            bw.newLine();
            bw.write(String.valueOf(degree));
            bw.newLine();
            bw.write(String.valueOf(spacing));
            bw.newLine();
            if(points!=null)
            {
                for (int total = 0; total < points.size(); total++)
                {
                    bw.write( points.get(total).longitude + "," +points.get(total).latitude );
                    bw.newLine();
                }
                bw.close();
            }
        }catch(IOException e){
            e.printStackTrace();
        }

    }
    @Override
protected void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);
        outState.putString(DEVICE_NAME, deviceName);
        if (logTextView != null) {
        outState.putString(LOG, logHtml.toString());
        }
        }
private boolean isConnected()
    {
        return (connector != null) && (connector.getState() == DeviceConnector.STATE_CONNECTED);
    }
private void stopConnection()
    {
        if (connector != null) {
        connector.stop();
        connector = null;
        deviceName = null;
        }
    }
private void startDeviceListActivity()
    {
        stopConnection();
        Intent serverIntent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
    }
private void drawline()
    {

        Ply = mMap.addPolygon(new PolygonOptions()
                .addAll(calcMaxMin(points,j))
                .strokeWidth(3)
                .strokeColor(Color.BLUE));
        Plycenter = mMap.addMarker(new MarkerOptions()
                .position(calcCenter(Ply.getPoints(), j))
                .draggable(true)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
        points.add(points.get(0));
        double pointlat[] = new double[Ply.getPoints().size()];
        double pointlng[] = new double[Ply.getPoints().size()];
        double EqualLatP[] = new double[Ply.getPoints().size()];
        for (int r = 0; r < Ply.getPoints().size(); r++) {
            EqualLatP[r] = Ply.getPoints().get(r).latitude;

        }
        double EqualLat = ((MAX(EqualLatP)) - (MIN(EqualLatP))) / PB;//計算線條

        for (int u = 0; Ply.getPoints().size() > u; u++) {
            pointlat[u] = Ply.getPoints().get(u).latitude;
            pointlng[u] = Ply.getPoints().get(u).longitude;
        }

        for (int hj = 0; hj < PB; hj++) {
            for (int hk = 1; hk < points.size(); hk++) {
                double x1 = MAX(pointlng), y1 = MIN(pointlat) + (EqualLat * (hj));      //第一條線第一個點
                double x2 = MIN(pointlng), y2 = MIN(pointlat) + (EqualLat * (hj));      //第一條線第二個點
                double x3 = points.get(hk - 1).longitude, y3 = points.get(hk - 1).latitude;                     //第二條線第一個點
                double x4 = points.get(hk).longitude, y4 = points.get(hk).latitude;                   //第二條線第二個點

                double x = ((x1 - x2) * (x3 * y4 - x4 * y3) - (x3 - x4) * (x1 * y2 - x2 * y1))
                        / ((x3 - x4) * (y1 - y2) - (x1 - x2) * (y3 - y4));
                double y = ((y1 - y2) * (x3 * y4 - x4 * y3) - (x1 * y2 - x2 * y1) * (y3 - y4))
                        / ((y1 - y2) * (x3 - x4) - (x1 - x2) * (y3 - y4));

                if (MIN(pointlat) < y & y < MAX(pointlat) & MIN(pointlng) < x & x < MAX(pointlng))
                {
                    linemarkers = mMap.addMarker(new MarkerOptions()
                            .position(new LatLng(y, x))
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));
                    OutSide.add(linemarkers.getPosition());
                }
            }
        }
        for (int lk = 0; lk < OutSide.size() - 3; lk = lk + 4) {
            Collections.swap(OutSide, 2 + lk, 3 + lk);
        }

        for (int total = 1; total < OutSide.size(); total++) {

            allline = mMap.addPolyline(new PolylineOptions()
                    .add(OutSide.get(total - 1), OutSide.get(total))
                    .width(10)
                    .color(Color.GREEN)

            );
        }
    }
    public LatLng transform(double x, double y, double tx, double ty, double deg,double sx,double sy)
    {
        deg = deg * Math.PI / 180;
        if (sy==0) sy = 1;
        if (sx==0) sx = 1;

        return new LatLng(sx * ((x - tx) * Math.cos(deg) - (y - ty) * Math.sin(deg)) + tx, sy * ((x - tx) * Math.sin(deg) + (y - ty) * Math.cos(deg)) + ty);
    }
    public double getDistance(double lat1, double lon1, double lat2, double lon2)
    {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0];
    }
    @Override
    public boolean onSearchRequested()
    {
        if (super.isAdapterReady()) startDeviceListActivity();
        return false;
    }
    public void onClick(View v)
    {
        switch (v.getId()) {

            case R.id.menu_enable:
                if (super.isAdapterReady()) {
                    if (isConnected()) stopConnection();
                    else startDeviceListActivity();
                } else {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }
                break;

            case R.id.menu_clear:
                if (logTextView != null) logTextView.setText("");
                break;

            case R.id.menu_send:
                if (logTextView != null) {
                    final String msg = logTextView.getText().toString();
                    final Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TEXT, msg);
                    startActivity(Intent.createChooser(intent, getString(R.string.menu_send)));
                }
                break;

            case R.id.menu_setting:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                break;

            case R.id.about:
                Intent intention = new Intent(this, AboutActivity.class);
                startActivity(intention);
                break;

            default:

                //return super.onOptionsItemSelected(item);
        }
    }
    @Override
    public void onStart()
    {
        super.onStart();
        // hex mode
        final String mode = Utils.getPrefence(this, getString(R.string.pref_commands_mode));
        this.hexMode = "HEX".equals(mode);
        if (hexMode) {
            commandEditText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
            commandEditText.setFilters(new InputFilter[]{new Utils.InputFilterHex()});
        } else {
            commandEditText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            commandEditText.setFilters(new InputFilter[]{});
        }
        // checksum
        final String checkSum = Utils.getPrefence(this, getString(R.string.pref_checksum_mode));
        this.checkSum = "Modulo 256".equals(checkSum);
        this.command_ending = getCommandEnding();
        this.show_timings = Utils.getBooleanPrefence(this, getString(R.string.pref_log_timing));
        this.show_direction = Utils.getBooleanPrefence(this, getString(R.string.pref_log_direction));
        this.needClean = Utils.getBooleanPrefence(this, getString(R.string.pref_need_clean));



    }
    private String getCommandEnding()
    {
        String result = Utils.getPrefence(this, getString(R.string.pref_commands_ending));
        if (result.equals("\\r\\n")) result = "\r\n";
        else if (result.equals("\\n")) result = "\n";
        else if (result.equals("\\r")) result = "\r";
        else result = "";
        return result;
    }
    //@TargetApi(Build.VERSION_CODES.KITKAT)
    //@RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    String address = data.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    BluetoothDevice device = btAdapter.getRemoteDevice(address);
                    //device.setPin(new byte[]{Byte.parseByte("YJ902UI")});
                    if (super.isAdapterReady() && (connector == null)) setupConnector(device);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                super.pendingRequestEnableBt = false;
                if (resultCode != Activity.RESULT_OK) {
                    Utils.log("BT not enabled");
                }
                break;
        }
    }
    private void setupConnector(BluetoothDevice connectedDevice)
    {
        stopConnection();
        try {
            String emptyName = getString(R.string.empty_device_name);
            DeviceData data = new DeviceData(connectedDevice, emptyName);
            connector = new DeviceConnector(data, mHandler);
            connector.connect();
        } catch (IllegalArgumentException e) {
            Utils.log("setupConnector failed: " + e.getMessage());
        }
    }
    public int checksum(String pucFrame, int usLen)
    {
        int chk=0;
        int i =0;
        byte[] AAA = new String(pucFrame).getBytes();
        while(usLen > 0) {
            usLen--;
            chk += (AAA[i] ^ i);
            i++;
        }
        return (chk & 0x00ffff);
    }
    public int myCRC16(String pucFrame, int usLen)
    {
        int ucCRCHi = 0xFF;
        int ucCRCLo = 0xFF;
        int iIndex,i=0;


        byte[] AAA = new String(pucFrame).getBytes();
        while(usLen > 0)
        {
            usLen--;
            iIndex = (ucCRCLo ^ ( AAA[i] )) & 0x00ff;
            i++;
            ucCRCLo = ( ucCRCHi ^ (aucCRCHi[iIndex] & 0x00ff) ) & 0x00ff;
            ucCRCHi = (aucCRCLo[iIndex]) & 0x00ff;
        }
        return ( ucCRCHi << 8 | ucCRCLo );
    }
    int CameraOperationtime=0;

    public void flymode()
    {
        TextView TV = (TextView)findViewById(R.id.TV6);
        TV.setText(String.valueOf(CameraOperationtime));
        TextView autopilot = (TextView)findViewById(R.id.autopilot);
        TextView cameracount = (TextView)findViewById(R.id.cameracount);
        TextView point = (TextView)findViewById(R.id.point);
        point.setText(String.valueOf(nextpoint+1));



        switch (autoclac)
        {
            case 0:
                if(Double.parseDouble(UAV[14]) < touchpoint[j][2])/**判斷高度 <    實際高度      高度提高    =>  轉頭+相機角度   =>    位置  */
                {
                    autoclac=1;
                    autopilot.setText("判斷高度");
                    point.setText(String.valueOf(nextpoint+1));
                    break;
                }
                if(Double.parseDouble(UAV[14]) >= touchpoint[j][2])/**判斷高度 >= 實際高度    轉頭+相機角度  =>     高度提高    =>    位置*/
                {
                    autoclac=3;
                    autopilot.setText("判斷高度");
                    point.setText(String.valueOf(nextpoint+1));
                    break;
                }

            case 1:/**判斷高度差 >= 設定值 高度提高*/
                if (Math.abs(Math.abs(touchpoint[j][2] - Double.parseDouble(UAV[14]))) <= 0.5)/**判斷高度差 >= 設定值 高度提高*/
                {
                    autoclac=2;
                    break;
                }
                else
                {
                    DL[nextpoint][0]    /** Longitude*/ = Double.valueOf(999);
                    DL[nextpoint][1]    /** Latitude*/ = Double.valueOf(99);
                    DL[nextpoint][2]    /** Altitude*/           = Double.valueOf(ValueAlt);
                    DL[nextpoint][3]    /** Heading*/           = Double.valueOf(999);
                    DL[nextpoint][4]    /** Navi_Speed*/        = Double.valueOf(99);
                    DL[nextpoint][5]    /** Camera_Pitch*/      = Double.valueOf(999);
                    DL[nextpoint][6]    /** Camera_Heading*/    = Double.valueOf(999);
                    CameraOperation     /**CameraOperation*/    =  0;   /**     0=none	1=Half	2=Shoot	3=Zin	4=Zout	5=Record    */
                    autopilot.setText("傳送高度");
                    break;
                }
            case 2:

                if(getDistance(OutSide.get(nextpoint).latitude,OutSide.get(nextpoint).longitude,Double.valueOf(UAV[13]),Double.valueOf(UAV[12]))    <= 4)
                {
                    autoclac=5;
                    break;
                }
                else
                {
                    DL[nextpoint][0]    /** Longitude*/ = OutSide.get(nextpoint).longitude;
                    DL[nextpoint][1]    /** Latitude*/ = OutSide.get(nextpoint).latitude;
                    DL[nextpoint][2]    /** Altitude*/ = Double.valueOf(9999);
                    DL[nextpoint][3]    /** Heading*/ = Double.valueOf(999);
                    DL[nextpoint][4]    /** Navi_Speed*/ = (double) ValueSpeed;
                    DL[nextpoint][5]    /** Camera_Pitch*/ = Double.valueOf(999);
                    DL[nextpoint][6]    /** Camera_Heading*/ = Double.valueOf(999);
                    CameraOperation     /**CameraOperation*/ = 0;   /**     0=none	1=Half	2=Shoot	3=Zin	4=Zout	5=Record    */
                    autopilot.setText("傳送位置");
                    break;
                }


            case 3:
                if(getDistance(OutSide.get(nextpoint).latitude,OutSide.get(nextpoint).longitude,Double.valueOf(UAV[13]),Double.valueOf(UAV[12]))    <= 4)
                {
                    autoclac=4;
                    break;
                }
                else
                {
                    DL[nextpoint][0]    /** Longitude*/ = OutSide.get(nextpoint).longitude;
                    DL[nextpoint][1]    /** Latitude*/ = OutSide.get(nextpoint).latitude;
                    DL[nextpoint][2]    /** Altitude*/ = Double.valueOf(9999);
                    DL[nextpoint][3]    /** Heading*/ = Double.valueOf(999);
                    DL[nextpoint][4]    /** Navi_Speed*/ = (double) ValueSpeed;
                    DL[nextpoint][5]    /** Camera_Pitch*/ = Double.valueOf(999);
                    DL[nextpoint][6]    /** Camera_Heading*/ = Double.valueOf(999);
                    CameraOperation     /**CameraOperation*/ = 0;   /**     0=none	1=Half	2=Shoot	3=Zin	4=Zout	5=Record    */
                    autopilot.setText("傳送位置");

                    break;
                }
            case 4:
                if (Math.abs(Math.abs(touchpoint[j][2] - Float.parseFloat(UAV[14]))) <= 0.5)/**判斷高度差 >= 設定值 高度提高*/
                {
                    autoclac=5;
                    break;
                }
                else
                {
                    DL[nextpoint][0]    /** Longitude*/ = Double.valueOf(999);
                    DL[nextpoint][1]    /** Latitude*/ = Double.valueOf(99);
                    DL[nextpoint][2]    /** Altitude*/           = Double.valueOf(ValueAlt);
                    DL[nextpoint][3]    /** Heading*/           = Double.valueOf(999);
                    DL[nextpoint][4]    /** Navi_Speed*/        = Double.valueOf(99);
                    DL[nextpoint][5]    /** Camera_Pitch*/      = Double.valueOf(999);
                    DL[nextpoint][6]    /** Camera_Heading*/    = Double.valueOf(999);
                    CameraOperation     /**CameraOperation*/    =  0;   /**     0=none	1=Half	2=Shoot	3=Zin	4=Zout	5=Record    */
                    autopilot.setText("傳送高度");
                    break;
                }
            case 5:/**    判斷轉頭+相機角度 = 設定值*/
                if (Math.abs( Double.valueOf(UAV[5]) - ValueHeading ) < 3 | Math.abs( Double.valueOf(UAV[5]) - ValueHeading ) > 360+3)/**判斷是否在值域內*/
                {/**值域-180~180*/

                    DL[nextpoint][0]    /** Longitude*/ = Double.valueOf(999);
                    DL[nextpoint][1]    /** Latitude*/ = Double.valueOf(99);
                    DL[nextpoint][2]    /** Altitude*/           = Double.valueOf(9999);
                    DL[nextpoint][3]    /** Heading*/           = Double.valueOf(ValueHeading);
                    DL[nextpoint][4]    /** Navi_Speed*/        = Double.valueOf(99);
                    DL[nextpoint][5]    /** Camera_Pitch*/      = Double.valueOf(ValueCH);
                    DL[nextpoint][6]    /** Camera_Heading*/    = Double.valueOf(999);
                    CameraOperation     /**CameraOperation*/    =  0;   /**     0=none	1=Half	2=Shoot	3=Zin	4=Zout	5=Record    */
                    autoclac=6;
                    cameracount.setText(String.valueOf(CameraOperation));

                    break;
                }
                else
                {
                    DL[nextpoint][0]    /** Longitude*/ = Double.valueOf(999);
                    DL[nextpoint][1]    /** Latitude*/ = Double.valueOf(99);
                    DL[nextpoint][2]    /** Altitude*/           = Double.valueOf(9999);
                    DL[nextpoint][3]    /** Heading*/           = Double.valueOf(ValueHeading);
                    DL[nextpoint][4]    /** Navi_Speed*/        = Double.valueOf(99);
                    DL[nextpoint][5]    /** Camera_Pitch*/      = Double.valueOf(ValueCH);
                    DL[nextpoint][6]    /** Camera_Heading*/    = Double.valueOf(999);
                    CameraOperation     /**CameraOperation*/    =  0;   /**     0=none	1=Half	2=Shoot	3=Zin	4=Zout	5=Record    */
                    cameracount.setText(String.valueOf(CameraOperation));
                    autopilot.setText("傳送航向");
                    break;
                }
            case 6:
                //if(CameraOperationtime>=4){CameraOperationtime=0;}
                if( CameraOperationtime <20){ CameraOperation     /**CameraOperation*/ = 2;   /**     0=none	1=Half	2=Shoot	3=Zin	4=Zout	5=Record    */}
                if( CameraOperationtime >=20)
                {
                    DL[nextpoint][0]    /** Longitude*/ = Double.valueOf(999);
                    DL[nextpoint][1]    /** Latitude*/ = Double.valueOf(99);
                    DL[nextpoint][2]    /** Altitude*/ = Double.valueOf(9999);
                    DL[nextpoint][3]    /** Heading*/ = Double.valueOf(999);
                    DL[nextpoint][4]    /** Navi_Speed*/ = Double.valueOf(99);
                    DL[nextpoint][5]    /** Camera_Pitch*/ = Double.valueOf(999);
                    DL[nextpoint][6]    /** Camera_Heading*/ = Double.valueOf(999);
                    CameraOperation     /**CameraOperation*/ = 2;   /**     0=none	1=Half	2=Shoot	3=Zin	4=Zout	5=Record    */
                    autopilot.setText("快門按下");
                    cameracount.setText(String.valueOf(CameraOperation));
                    autoclac=7;
                    break;
                }
                else {

                    CameraOperationtime++;

                    DL[nextpoint][0]    /** Longitude*/ = Double.valueOf(999);
                    DL[nextpoint][1]    /** Latitude*/ = Double.valueOf(99);
                    DL[nextpoint][2]    /** Altitude*/ = Double.valueOf(9999);
                    DL[nextpoint][3]    /** Heading*/ = Double.valueOf(999);
                    DL[nextpoint][4]    /** Navi_Speed*/ = Double.valueOf(99);
                    DL[nextpoint][5]    /** Camera_Pitch*/ = Double.valueOf(999);
                    DL[nextpoint][6]    /** Camera_Heading*/ = Double.valueOf(999);
                    CameraOperation     /**CameraOperation*/ = 2;   /**     0=none	1=Half	2=Shoot	3=Zin	4=Zout	5=Record    */
                    cameracount.setText(String.valueOf(CameraOperation));
                    autopilot.setText("快門按下");
                    break;
                }
            case 7:
                if( CameraOperationtime >=60)
                {

                    if(nextpoint>OutSide.size()-2)
                    {
                        //DeviceControlActivity.imageButton.setImageResource(R.drawable.misson);
                        handler.removeCallbacks(updateThread);
                        handler.removeCallbacks(UpdateRate);
                        CameraOperationtime=0;
                        god=0;
                        flag=0;

                        //nextpoint=0;
                        new AlertDialog.Builder(DeviceControlActivity.this)
                                .setTitle("執行完成")//設定視窗標題
                                .setIcon(R.mipmap.ic_launcher)//設定對話視窗圖示
                                .setMessage("已經執行完成了唷!!" +
                                        "按下'確定'關閉此視窗")//設定顯示的文字
                                .setPositiveButton("確定",new DialogInterface.OnClickListener(){
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        ImageButton sendbutton= (ImageButton) findViewById(R.id.sendbutton);
                                        sendbutton.setImageResource(R.drawable.misson);
//                                        handler.removeCallbacks(updateThread);
//                                        //handler.removeCallbacks(Cpature);
//                                        handler.removeCallbacks(UpdateRate);
                                        handler.removeCallbacks(updateThread);
                                        handler.removeCallbacks(UpdateRate);
                                        autoclac=0;
//                                        god=0;
                                        nextpoint=0;
                                        key[0] = 0;
                                    }
                                })//設定結束的子視窗
                                .show();//呈現對話視窗

                        //break;
                    }
                    else
                    {
//                        handler.removeCallbacks(updateThread);
//                        handler.removeCallbacks(Cpature);
//                        handler.removeCallbacks(UpdateRate);
                        if(CameraOperationtime>=60){CameraOperationtime=0;}
                        what=0;
                        //handler.removeCallbacks(Cpature);
                        nextpoint=nextpoint+1;
                        autoclac=0;
                        break;
                    }
                }
                else {
                    DL[nextpoint][0]    /** Longitude*/ = Double.valueOf(999);
                    DL[nextpoint][1]    /** Latitude*/ = Double.valueOf(99);
                    DL[nextpoint][2]    /** Altitude*/ = Double.valueOf(9999);
                    DL[nextpoint][3]    /** Heading*/ = Double.valueOf(999);
                    DL[nextpoint][4]    /** Navi_Speed*/ = Double.valueOf(99);
                    DL[nextpoint][5]    /** Camera_Pitch*/ = Double.valueOf(999);
                    DL[nextpoint][6]    /** Camera_Heading*/ = Double.valueOf(999);
                    if( CameraOperationtime <20){ CameraOperation     /**CameraOperation*/ = 2;   /**     0=none	1=Half	2=Shoot	3=Zin	4=Zout	5=Record    */}
                    else {CameraOperation     /**CameraOperation*/ = 0;   /**     0=none	1=Half	2=Shoot	3=Zin	4=Zout	5=Record    */}
                    cameracount.setText(String.valueOf(CameraOperation));
                    autopilot.setText("快門放開");
                    CameraOperationtime++;
                    break;
                }
        }
    }

    public String FcsDL(String fcsdl, int pointk)
    {

        if(DL[pointk][0]==null){

            pointk=pointk-1;}
            if(pointk==-1){pointk=0;}
    fcsdl = AutoPilot + fcsdl.format("%11.6f, %10.6f, %7.1f,  %6.1f,      %5.1f,  %7.2f,  %7.2f,  %d, %5d"
                    , DL[pointk][0], DL[pointk][1], DL[pointk][2], DL[pointk][3], DL[pointk][4],    DL[pointk][5], DL[pointk][6],   CameraOperation,    2000);
/**$RCS+Auto Pilot=         Longitude,              Latitude,               Altitude,               Heading,                     Camera_Pitch,            Navi_Speed,           Camera_Heading,     Camera_Operation,       AGR_Dosage    #Check*/

    int len = fcsdl.length();
    int CRC16 = checksum(fcsdl, len);
    String CRC16S = Integer.toHexString(CRC16).toUpperCase();
    fcsdl = fcsdl + "#" + CRC16S;

        return fcsdl;

}
    String commandString;

    public void sendCommand(View view)
    {
                if(key[0] ==1) {

                    commandString = FcsDL(commandString, nextpoint);
                    byte[] command = (hexMode ? Utils.toHex(commandString) : commandString.getBytes());
                    if (command_ending != null) command = Utils.concat(command, command_ending.getBytes());
                    if (isConnected()) {


                        connector.write(command);
                        appendLog(commandString, hexMode, true, needClean);
                    }

                }
                if(key[0] ==0)
                {
                    god=0;
                    flag=0;
                    handler.removeCallbacks(updateThread);
                    handler.removeCallbacks(UpdateRate);

                }
            if (commandString.isEmpty()) return;
        }
    //}
    public void Test()
    {
        if(OutSide.size() != 0 ) {
                if ((getDistance(testpointlat, testpointlng, OutSide.get(k).latitude, OutSide.get(k).longitude)) < 2) {
                    k=k+1;
                    if(k == OutSide.size()) {
                        k=0;
                    }
                }
                float m = (float) 0.3;
                testpointlng = ((OutSide.get(k).longitude) * (m)) + ((1 - m) * pretestpointlng);
                testpointlat = ((OutSide.get(k).latitude) * (m)) + ((1 - m) * pretestpointlat);

                pretestpointlng = testpointlng;
                pretestpointlat = testpointlat;

            if (PreTestLatLng != null) {
                PreTestLatLng.remove();
            }
            TestLatLng = mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(testpointlat, testpointlng))
                    .draggable(true)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
            );
            PreTestLatLng = TestLatLng;
        }

        if (testpointlat != 0) {
            if (PolylinenextPoint != null) {
                PlanePolyline = mMap.addPolyline(new PolylineOptions()
                        .add(new LatLng(testpointlat, testpointlng), PolylinenextPoint)
                        .width(2)
                        .color(Color.YELLOW));
            }
            PrePlanePolyline = PlanePolyline;
            PolylinenextPoint = new LatLng(testpointlat, testpointlng);
        }
    }
    public double MAX(double arr[])
    {
        double max=arr[0];         // <-從此開始
        for(int i=0;i<arr.length;i++) {
            if (max < arr[i])
            {max = arr[i];}
        }return max;
    }
    public double MIN(double arr[])
    {
        double min=arr[0];         // <-從此開始
        for(int i=0;i<arr.length;i++) {
            if (min > arr[i])
            {min = arr[i];}
        }return min;
    }
    public List<LatLng> calcMaxMin(List<LatLng> latlng,int t)
    {
        /**東北   NE   =     maxlatlng[0][0]=MaxLat      maxlatlng[0][1]=MaxLng*/
        /**西北   NW  =     maxlatlng[1][0]=MaxLat      maxlatlng[1][1]=MinLng*/
        /**東南   SE   =     maxlatlng[2][0]=MinLat      maxlatlng[2][1]=MaxLng*/
        /**西南   SW   =     maxlatlng[3][0]=MinLat      maxlatlng[3][1]=MinLng*/
        /**LatLng NE     = new LatLng(MAX(pointlat),MAX(pointlng));
                /**LatLng NW    = new LatLng(MAX(pointlat),MIN(pointlng));
                /**LatLng SE        = new LatLng(MIN(pointlat),MAX(pointlng));
                /**LatLng SW     = new LatLng(MIN(pointlat),MIN(pointlng));*/
        double pointlat[]=new double[t];
        double pointlng[]=new double[t];
        List<LatLng> ALL = new ArrayList<>() ;
        for( t=0;latlng.size()>t;t++){
            pointlat[t]=latlng.get(t).latitude;
            pointlng[t]=latlng.get(t).longitude;
        }
        ALL.add(new LatLng(MAX(pointlat),MAX(pointlng)));
        ALL.add(new LatLng(MAX(pointlat),MIN(pointlng)));
        ALL.add(new LatLng(MIN(pointlat),MIN(pointlng)));
        ALL.add(new LatLng(MIN(pointlat),MAX(pointlng)));
        return ALL;
    }
    public LatLng calcCenter(List<LatLng> latlng,int t)
    {
        double pointlat[]=new double[t+1];
        double pointlng[]=new double[t+1];

        for(int v=0;latlng.size()-1>v;v++){
            pointlat[v]=latlng.get(v).latitude;
            pointlng[v]=latlng.get(v).longitude;
        }
        return new LatLng( (MAX(pointlat) + MIN(pointlat) ) /2 , ( MAX(pointlng) + MIN(pointlng) ) /2);
    }
    int Device_count=0;
    private void  appendLog(String message, boolean hexMode, boolean outgoing, boolean clean)
    {

        //        TextView UTC_Date = (TextView) findViewById(R.id.textView);
//       TextView UTC_Time = (TextView) findViewById(R.id.textView2);
        TextView AAHRS_Roll = (TextView) findViewById(R.id.textView12);
        TextView AAHRS_Pitch = (TextView) findViewById(R.id.textView7);
        TextView AAHRS_Yaw = (TextView) findViewById(R.id.textView5);
//        TextView GPS_COG = (TextView) findViewById(R.id.textView6);
        TextView GPS_Speed = (TextView) findViewById(R.id.textView13);
//        TextView AAHRS_Climb_Rate = (TextView) findViewById(R.id.textView8);
//        TextView Auto_Pilot_Back = (TextView) findViewById(R.id.textView9);
//        TextView Auto_Pilot_Status = (TextView) findViewById(R.id.textView10);
        TextView Camera_Pitch = (TextView) findViewById(R.id.textView1);
        TextView Longitude = (TextView) findViewById(R.id.textView9);
        TextView Latitude = (TextView) findViewById(R.id.textView11);
        TextView AAHRS_Altitude = (TextView) findViewById(R.id.textView14);
//        TextView Motor_Total_Power = (TextView) findViewById(R.id.textView15);
//        TextView Battery_Cell = (TextView) findViewById(R.id.textView16);
        TextView Volt_ori = (TextView) findViewById(R.id.textView2);
        TextView Curr = (TextView) findViewById(R.id.textView3);
//        TextView mAh = (TextView) findViewById(R.id.textView19);
        TextView Temp = (TextView) findViewById(R.id.textView4);
//        TextView GPS_Quality = (TextView) findViewById(R.id.textView21);
//        TextView Take_off = (TextView) findViewById(R.id.textView22);
//        TextView Landing = (TextView) findViewById(R.id.textView23);
//        TextView GoH = (TextView) findViewById(R.id.textView24);
//        TextView GoP = (TextView) findViewById(R.id.textView25);
//        TextView RPM = (TextView) findViewById(R.id.textView26);
//        TextView ARG_Time = (TextView) findViewById(R.id.textView27);
//        TextView ARG_F = (TextView) findViewById(R.id.textView28);
//        TextView ARG_P = (TextView) findViewById(R.id.textView29);
        TextView Volt = (TextView) findViewById(R.id.textView);

        StringBuilder msg = new StringBuilder();
        if (show_timings) msg.append("[").append(timeformat.format(new Date())).append("]");
        if (show_direction) {
            final String arrow = (outgoing ? " << " : " >> ");
            msg.append(arrow);
        } else msg.append(" ");

            message = message.replace("\r", "").replace("\n", "");
            String[] checkstart = message.split("=");


            if (TEST.equals(checkstart[0])) {
                handler.post(test);
            }
            if (TESTSTOP.equals(checkstart[0])) {
                handler.removeCallbacks(test);
            }

            if (FCSDL.equals(checkstart[0])) {
                UAV = message.split("[=,#\\s]+");
                String[] check = message.split("#");
                int len = check[0].length();
                int CRC16 = checksum(check[0], len);
                int intValue = Integer.parseInt(check[1], 16);

                if (CRC16 == intValue)
                {

                    float UAV_Batt = Float.parseFloat(UAV[17]);
                    float UAV_Cell = Float.parseFloat(UAV[16]);
                    UAV_Vehicle_Batt = (float) (((UAV_Batt / UAV_Cell) - (3.7)) * 100 / 0.5);

                    Volt.setText("Vehicle Batt : " + UAV_Vehicle_Batt + "%");
                    Volt_ori.setText("電壓 : " + UAV[17] + " V");
                    Curr.setText("電流 : " + UAV[18] + " A");
                    Temp.setText("溫度 : " + UAV[20] + " °C");
                    AAHRS_Roll.setText(UAV[3] + "°");
                    AAHRS_Pitch.setText(UAV[4] + "°");
                    UAV[5] = String.valueOf((Float.parseFloat(UAV[5]) ));
                    AAHRS_Yaw.setText(UAV[5] + "°");
                    GPS_Speed.setText(UAV[7]);
                    Longitude.setText(UAV[12]);
                    Latitude.setText(UAV[13]);
                    AAHRS_Altitude.setText(UAV[14]);
                    Camera_Pitch.setText(UAV[11] + "°");

                    ImageView imageView2 = (ImageView) findViewById(R.id.imageView2);

                    imageView2.buildDrawingCache();
                    //取得緩存圖片的Bitmap檔
                    Bitmap bmp = imageView2.getDrawingCache();
                    //定義一個矩陣圖
                    Matrix m = new Matrix();
                    //取得圖片的寬度
                    int width = bmp.getWidth();
                    //取得圖片的長度
                    int height = bmp.getHeight();
                    //逆時針旋轉90度
                    m.setRotate(Float.parseFloat(UAV[5]), Float.parseFloat(UAV[4]), Float.parseFloat(UAV[3]));
                    //產生新的旋轉後Bitmap檔
                    Bitmap b = Bitmap.createBitmap(bmp, 0, 0, width, height, m, true);

                    Device_count++;
                    if(Device_count>=100){Device_count=100;}

                    if ( key[0] == 1 &UAV[10].equals("2") & UAV_Cell > 0 & Device_count >= 100) {
                        Device_count=0;
                        key[0] = 0;
                        handler.removeCallbacks(updateThread);
                        handler.removeCallbacks(UpdateRate);
                        CameraOperationtime=0;
                        god=0;
                        flag=0;
                        autoclac=0;
                        //handler.removeCallbacks(Cpature);
                        new AlertDialog.Builder(DeviceControlActivity.this)
                                .setTitle("暫停")//設定視窗標題
                                .setIcon(R.mipmap.ic_launcher)//設定對話視窗圖示
                                .setMessage("遙控器介入唷!!" +
                                        "按下'確定'關閉此視窗")//設定顯示的文字
                                .setPositiveButton("確定", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        ImageButton sendbutton= (ImageButton) findViewById(R.id.sendbutton);
                                        sendbutton.setImageResource(R.drawable.misson);
                                        handler.removeCallbacks(updateThread);
                                        handler.removeCallbacks(UpdateRate);
                                        nextpoint=0;
                                        key[0] = 0;
                                        //key[0] = 0;
                                    }
                                })//設定結束的子視窗
                                .show();//呈現對話視窗
                    }

                    //顯示圖片
                    imageView2.setImageBitmap(b);
                    if (UAV[12] != null) {
                        if (PrePlaneLatLng != null) {
                            PrePlaneLatLng.remove();
                        }
                        PlaneLatLng = mMap.addMarker(new MarkerOptions()
                                .position(new LatLng(Double.parseDouble(UAV[13]), Double.parseDouble(UAV[12])))
                                .draggable(true)
                                .title("Plane")
                                //.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE))
                                .icon(BitmapDescriptorFactory.fromResource(R.drawable.marker70368))
                                .zIndex(1.0f)
                        );
                        PrePlaneLatLng = PlaneLatLng;
                    }

                    Switch Polylineonoff = (Switch) findViewById(R.id.polylineswitch);
                    //Polylineonoff.setChecked(true);
                    Polylineonoff.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            if (isChecked == true) {

                                Polyline_onoff_switch =true;
                            }
                            else if(isChecked == false &PlanePolyline!=null) {
                                Polyline_onoff_switch = false;
                                PlanePolyline.remove();
                            }
                        }
                    });
                    if (UAV[12] != null & Polyline_onoff_switch==true) {
                        if (PolylinenextPoint != null) {
                            PlanePolyline = mMap.addPolyline(new PolylineOptions()
                                    .add(new LatLng(Double.parseDouble(UAV[13]), Double.parseDouble(UAV[12])), PolylinenextPoint)
                                    .width(2)
                                    .color(Color.YELLOW));
                        }
                        PrePlanePolyline = PlanePolyline;
                        PolylinenextPoint = new LatLng(Double.parseDouble(UAV[13]), Double.parseDouble(UAV[12]));
                    }
                    int progressBarBat = (int) UAV_Vehicle_Batt;
                    progressBar.setProgress(progressBarBat);
                    Matrix matrix = new Matrix();
                    float UAW_Camera_pitch = Float.parseFloat(UAV[11]);
                    matrix.postRotate(UAW_Camera_pitch);
                } else return;
            } else return;



        String crc = "";
        boolean crcOk = false;
        if (checkSum) {
            int crcPos = message.length() - 2;
            crc = message.substring(crcPos);
            message = message.substring(0, crcPos);
            crcOk = outgoing || crc.equals(Utils.calcModulo256(message).toUpperCase());
            if (hexMode) crc = Utils.printHex(crc.toUpperCase());
        }

        msg.append("<b>")
                .append(hexMode ? Utils.printHex(message) : message)
                .append(checkSum ? Utils.mark(crc, crcOk ? CRC_OK : CRC_BAD) : "")
                .append("</b>")
                .append("<br>");

        logHtml.append(msg);
        logTextView.append(Html.fromHtml(msg.toString()));

        final int scrollAmount = logTextView.getLayout().getLineTop(logTextView.getLineCount()) - logTextView.getHeight();
        if (scrollAmount > 0)
            logTextView.scrollTo(0, scrollAmount);
        else logTextView.scrollTo(0, 0);

        if (clean) commandEditText.setText("");
    }
    void setDeviceName(String deviceName)
    {
        this.deviceName = deviceName;
        getActionBar().setSubtitle(deviceName);

    }
    private static class BluetoothResponseHandler extends Handler
    {
        private WeakReference<DeviceControlActivity> mActivity;

        public BluetoothResponseHandler(DeviceControlActivity activity) {
            mActivity = new WeakReference<DeviceControlActivity>(activity);
        }

        public void setTarget(DeviceControlActivity target) {
            mActivity.clear();
            mActivity = new WeakReference<DeviceControlActivity>(target);
        }

        @Override
        public void handleMessage(Message msg) {
            DeviceControlActivity activity = mActivity.get();
            if (activity != null) {
                switch (msg.what) {
                    case MESSAGE_STATE_CHANGE:

                        Utils.log("MESSAGE_STATE_CHANGE: " + msg.arg1);
                        final ActionBar bar = activity.getActionBar();
                        switch (msg.arg1) {
                            case DeviceConnector.STATE_CONNECTED:
                                bar.setSubtitle(MSG_CONNECTED);
                                break;
                            case DeviceConnector.STATE_CONNECTING:

                                bar.setSubtitle(MSG_CONNECTING);
                                break;
                            case DeviceConnector.STATE_NONE:
                                bar.setSubtitle(MSG_NOT_CONNECTED);
                                break;
                        }
                        activity.invalidateOptionsMenu();
                        break;

                    case MESSAGE_READ:
                        final String readMessage = (String) msg.obj;
                        if (readMessage != null) {
                            activity.appendLog(readMessage, false, false, activity.needClean);
                            //activity.commandString=readMessage;
                        }
                        break;

                    case MESSAGE_DEVICE_NAME:
                        activity.setDeviceName((String) msg.obj);
                        break;

                    case MESSAGE_WRITE:
                        // stub
                        break;

                    case MESSAGE_TOAST:
                        // stub
                        break;
                }
            }
        }
    }
}
