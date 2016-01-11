package org.yacas.yacasforandroid;

import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;

import net.sf.yacas.YacasInterpreter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private YacasInterpreter yacasInterpreter;

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        ActionBar actionBar = getSupportActionBar();
        actionBar.setLogo(R.mipmap.ic_launcher);
        actionBar.setDisplayShowTitleEnabled(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        webView = (WebView) findViewById(R.id.webView);

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        webView.addJavascriptInterface(this, "yacas");

        webView.loadUrl("file:///android_asset/yagy_ui.html");


        try {
            yacasInterpreter = new YacasInterpreter();

            yacasInterpreter.Evaluate("Plot2D'outputs();");
            yacasInterpreter.Evaluate("UnProtect(Plot2D'outputs);");
            yacasInterpreter.Evaluate("Plot2D'yagy(values_IsList, _options'hash) <-- Yagy'Plot2D'Data(values, options'hash);");
            yacasInterpreter.Evaluate("Plot2D'outputs() := { {\"default\", \"yagy\"}, {\"data\", \"Plot2D'data\"}, {\"gnuplot\", \"Plot2D'gnuplot\"}, {\"java\", \"Plot2D'java\"}, {\"yagy\", \"Plot2D'yagy\"}, };");
            yacasInterpreter.Evaluate("Protect(Plot2D'outputs);");
            yacasInterpreter.Evaluate("Plot3DS'outputs();");
            yacasInterpreter.Evaluate("UnProtect(Plot3DS'outputs);");
            yacasInterpreter.Evaluate("Plot3DS'yagy(values_IsList, _options'hash) <-- Yagy'Plot3DS'Data(values, options'hash);");
            yacasInterpreter.Evaluate("Plot3DS'outputs() := { {\"default\", \"yagy\"}, {\"data\", \"Plot3DS'data\"}, {\"gnuplot\", \"Plot3DS'gnuplot\"}, {\"yagy\", \"Plot3DS'yagy\"},};");
            yacasInterpreter.Evaluate("Protect(Plot3DS'outputs);");

        } catch (Exception e) {
            // FIXME: handle it somehow
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                return true;
            case R.id.action_evaluate:
                webView.evaluateJavascript("evaluateCurrent();", null);
                return true;
            case R.id.action_add_above:
                webView.evaluateJavascript("insertBeforeCurrent();", null);
                return true;
            case R.id.action_add_below:
                webView.evaluateJavascript("insertAfterCurrent();", null);
                return true;
            case R.id.action_delete_current:
                webView.evaluateJavascript("deleteCurrent();", null);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @JavascriptInterface
    public void eval(int idx, String expr) {
        final String plot2dPrefix = "Yagy'Plot2D'Data(";
        final String plot3dsPrefix = "Yagy'Plot3DS'Data(";
        final String graphPrefix = "Graph(";

        final Pattern dictEntryRx = Pattern.compile("(\"[^\"]+\"),(.+)");
        final Pattern splitStringListRx = Pattern.compile("\",(?=(?:[^\\\"\"]*\\\"\"[^\\\"\"]*\\\"\")*(?![^\\\"\"]*\\\"\"))\"");

        String result = yacasInterpreter.Evaluate(expr).trim();

        final JSONObject evaluationResult = new JSONObject();

        try {
            evaluationResult.put("idx", idx);
            evaluationResult.put("input", expr);

            if (result.startsWith(plot2dPrefix)) {
                result = result.substring(plot2dPrefix.length(), result.length() - 1).trim();

                String[] parts = result.split(Pattern.quote("}},{{"));
                String optionsString = parts[parts.length - 1].trim();
                parts = Arrays.copyOf(parts, parts.length - 1);
                optionsString = optionsString.substring(0, optionsString.length() - 2);

                String[] labels = null;

                for (String os : optionsString.split(Pattern.quote("},{"))) {

                    Matcher dictEntryMatch = dictEntryRx.matcher(os);

                    dictEntryMatch.matches();

                    if (dictEntryMatch.group(1).equals("\"yname\"")) {
                        String s = dictEntryMatch.group(2);
                        s = s.substring(2, s.length() - 2);
                        labels = splitStringListRx.split(s);
                    }
                }

                JSONArray data = new JSONArray();

                for (int i = 0; i < parts.length; ++i) {
                    String part = parts[i];

                    JSONArray partialData = new JSONArray();

                    part = part.replaceAll(Pattern.quote("{{{"), "");
                    part = part.replaceAll(Pattern.quote("}}}"), "");

                    for (String ss : part.split(Pattern.quote("},{"))) {
                        JSONArray p = new JSONArray();
                        for (String s : ss.split(Pattern.quote(",")))
                            p.put(Double.parseDouble(s.replaceAll(Pattern.quote("{"), "").replaceAll(Pattern.quote("}"), "")));
                        partialData.put(p);
                    }
                    JSONObject dataEntry = new JSONObject();
                    dataEntry.put("label", labels[i]);
                    dataEntry.put("data", partialData);

                    data.put(dataEntry);
                }

                evaluationResult.put("type", "Plot2D");
                evaluationResult.put("plot2d_data", data);

            } else if (result.startsWith(plot3dsPrefix)) {
                result = result.substring(plot3dsPrefix.length(), result.length() - 1).trim();

                String[] parts = result.split(Pattern.quote("}},{{"));

                String optionsString = parts[parts.length - 1].trim();
                parts = Arrays.copyOf(parts, parts.length - 1);
                optionsString = optionsString.substring(0, optionsString.length() - 2);

                String[] labels = null;

                for (String os : optionsString.split(Pattern.quote("},{"))) {

                    Matcher dictEntryMatch = dictEntryRx.matcher(os);

                    dictEntryMatch.matches();

                    if (dictEntryMatch.group(1).equals("\"zname\"")) {
                        String s = dictEntryMatch.group(2);
                        s = s.substring(2, s.length() - 2);
                        labels = splitStringListRx.split(s);
                    }
                }

                JSONArray data = new JSONArray();

                for (int i = 0; i < parts.length; ++i) {
                    String part = parts[i];

                    JSONArray partialData = new JSONArray();

                    part = part.replaceAll(Pattern.quote("{{{"), "");
                    part = part.replaceAll(Pattern.quote("}}}"), "");

                    for (String ss : part.split(Pattern.quote("},{"))) {
                        JSONArray p = new JSONArray();
                        for (String s : ss.split(Pattern.quote(",")))
                            p.put(Double.parseDouble(s.replaceAll(Pattern.quote("{"), "").replaceAll(Pattern.quote("}"), "")));
                        partialData.put(p);
                    }
                    JSONObject dataEntry = new JSONObject();
                    dataEntry.put("label", labels[i]);
                    dataEntry.put("data", partialData);

                    data.put(dataEntry);
                }

                evaluationResult.put("type", "Plot3D");
                evaluationResult.put("plot3d_data", data);

            } else if (result.startsWith(graphPrefix)) {
                result = result.substring(graphPrefix.length(), result.length() - 1).trim();
                String[] parts = result.split(Pattern.quote("},{"));
                String[] vertices = parts[0].replace("{", "").split(",");

                JSONArray edges = new JSONArray();
                for (String es : parts[1].replace("}", "").split(",")) {
                    boolean bi = true;
                    String[] ft = es.split(Pattern.quote("<->"));
                    if (ft.length == 1) {
                        ft = es.split("->");
                        bi = false;
                    }

                    JSONObject edge = new JSONObject();

                    edge.put("from", Arrays.asList(vertices).indexOf(ft[0]) + 1);
                    edge.put("to", Arrays.asList(vertices).indexOf(ft[1]) + 1);
                    edge.put("bi", bi);

                    edges.put(edge);
                }

                evaluationResult.put("type", "Graph");
                evaluationResult.put("graph_vertices", new JSONArray(vertices));
                evaluationResult.put("graph_edges", edges);

            } else {

                String texCode = yacasInterpreter.Evaluate("TeXForm(Hold(" + result + "))");
                texCode = texCode.substring(2, texCode.length() - 2);

                boolean is_number = yacasInterpreter.Evaluate("IsNumber(Hold(" + result + "));").equals("True");
                boolean is_constant = yacasInterpreter.Evaluate("IsConstant(Hold(" + result + "));").equals("True");
                boolean is_vector = yacasInterpreter.Evaluate("IsVector(Hold(" + result + "));").equals("True");
                boolean is_matrix = yacasInterpreter.Evaluate("IsMatrix(Hold(" + result + "));").equals("True");
                boolean is_square_matrix = yacasInterpreter.Evaluate("IsSquareMatrix(Hold(" + result + "));").equals("True");

                evaluationResult.put("type", "Expression");
                evaluationResult.put("expression", result);
                evaluationResult.put("tex_code", texCode);

                evaluationResult.put("expression_type", "function");

                if (is_number)
                    evaluationResult.put("expression_type", "number");
                else if (is_constant && !(is_vector || is_matrix))
                    evaluationResult.put("expression_type", "constant");
                else if (is_vector)
                    evaluationResult.put("expression_type", "vector");
                else if (is_square_matrix)
                    evaluationResult.put("expression_type", "square_matrix");
                else if (is_matrix)
                    evaluationResult.put("expression_type", "matrix");

                String vars = yacasInterpreter.Evaluate("VarList(Hold(" + result + "));");
                vars = vars.substring(1, vars.length() - 1);
                evaluationResult.put("variables", new JSONArray(vars.split(",")));
            }

            webView.post(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript("printResults(" + evaluationResult.toString() + ");", new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String s) {
                        }
                    });
                }
            });
        } catch (JSONException e) {
            // FIXME: handle it somehow
        }
    }
}
