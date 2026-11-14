package btm.m.os4.iosnotificationcenter;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 40, 32, 32);
        root.setGravity(Gravity.TOP);
        
        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(22);
        root.addView(title);

        TextView status = new TextView(this);
        status.setText(R.string.scaffold_status);
        status.setTextSize(15);
        status.setPadding(0, 20, 0, 0);
        root.addView(status);

        setContentView(root);
    }
}
