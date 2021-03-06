package co.in.mobilepay.view.fragments;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import com.google.gson.Gson;
import com.squareup.otto.Subscribe;

import co.in.mobilepay.R;
import co.in.mobilepay.application.MobilePayAnalytics;
import co.in.mobilepay.bus.MobilePayBus;
import co.in.mobilepay.entity.UserEntity;
import co.in.mobilepay.json.request.RegisterJson;
import co.in.mobilepay.json.response.ResponseData;
import co.in.mobilepay.service.ServiceUtil;
import co.in.mobilepay.service.impl.MessageConstant;
import co.in.mobilepay.view.activities.ActivityUtil;
import co.in.mobilepay.view.activities.MainActivity;

/**
 * Created by Nithish on 06-02-2016.
 */
public class RegistrationFragment extends Fragment implements View.OnClickListener{
    EditText name = null;
    EditText email = null;
    EditText password = null;
    EditText rePassword = null;

    TextInputLayout nameFloatLabel = null;
    TextInputLayout emailFloatLabel = null;
    TextInputLayout passwordFloatLabel = null;
    TextInputLayout rePasswordFloatLabel = null;


    private MainActivity mainActivity = null;
    ProgressDialog progressDialog = null;
    private MainActivityCallback mainActivityCallback =null;

    RegisterJson registerJson;

    final private int REQUEST_CODE_ASK_PERMISSIONS = 123;

    public RegistrationFragment(){
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.mainActivity = (MainActivity)context;
        this.mainActivityCallback = mainActivity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_register, container, false);
        name = (EditText) view.findViewById(R.id.reg_name);
        email = (EditText) view.findViewById(R.id.reg_email);
        nameFloatLabel = (TextInputLayout) view.findViewById(R.id.input_layout_reg_name);
        emailFloatLabel = (TextInputLayout) view.findViewById(R.id.input_layout_reg_email);
        passwordFloatLabel = (TextInputLayout) view.findViewById(R.id.input_layout_reg_password);
        rePasswordFloatLabel = (TextInputLayout) view.findViewById(R.id.input_layout_reg_repassword);
        nameFloatLabel.setHintTextAppearance(R.style.FloatLabelColor);
        emailFloatLabel.setHintTextAppearance(R.style.FloatLabelColor);
        passwordFloatLabel.setHintTextAppearance(R.style.FloatLabelColor);
        rePasswordFloatLabel.setHintTextAppearance(R.style.FloatLabelColor);


        password = (EditText)view.findViewById(R.id.reg_password);
        rePassword = (EditText) view.findViewById(R.id.reg_repassword);
        FloatingActionButton floatingActionButton = (FloatingActionButton)view.findViewById(R.id.reg_submit);
        floatingActionButton.setOnClickListener(this);

        boolean isNet = ServiceUtil.isNetworkConnected(mainActivity);
        if(isNet){
            progressDialog = ActivityUtil.showProgress(getString(R.string.reg_submit_heading), getString(R.string.reg_submit_message), mainActivity);
            mainActivity.getAccountService().getUserProfile(mainActivity.getMobileNumber(),mainActivity);
        }else{
            loadData();
        }

        /*if(mainActivity.isPasswordForget()){

            boolean isNet = ServiceUtil.isNetworkConnected(mainActivity);
            if(isNet){
                progressDialog = ActivityUtil.showProgress("In Progress", "Loading...", mainActivity);
                mainActivity.getAccountService().getUserProfile(mainActivity);
            }else{
                loadData();
            }
            loadData();
        }*/
        return view;
    }

    public RegisterJson getRegistrationJson(){
        String nameTemp = name.getText().toString();
        if(nameTemp == null  || nameTemp.trim().isEmpty()){
            name.setError(getString(R.string.reg_name_error));
            name.requestFocus();
            return null;
        }
        String emailTemp = email.getText().toString();
        if(emailTemp == null  || emailTemp.trim().isEmpty()){
            email.setError(getString(R.string.reg_email_error));
            email.requestFocus();
            return null;
        }

        if(!Patterns.EMAIL_ADDRESS.matcher(emailTemp).matches()){
            email.setError(getString(R.string.reg_email_not_valid_error));
            email.setSelection(emailTemp.length());
            email.requestFocus();
            return null;
        }

        String passwordTemp = password.getText().toString();
        if(passwordTemp == null || passwordTemp.trim().isEmpty()){
            password.setError(getString(R.string.reg_pass_error));
            password.requestFocus();
            return null;
        }
        String rePasswordTemp = rePassword.getText().toString();
        if(rePasswordTemp == null || rePasswordTemp.trim().isEmpty()){
            rePassword.setError(getString(R.string.reg_re_pass_error));
            rePassword.requestFocus();
            return null;
        }
        if(!passwordTemp.equals(rePasswordTemp)){
            rePassword.setError(getString(R.string.reg_re_pass_not_same_error));
            rePassword.setSelection(rePasswordTemp.length());
            rePassword.requestFocus();
            return null;
        }

        if(passwordTemp.length() < 6){
            password.setError(getString(R.string.reg_pass_len_error));
            password.setSelection(rePasswordTemp.length());
            password.requestFocus();
            return null;
        }

        return new RegisterJson(nameTemp,passwordTemp,mainActivity.getMobileNumber(),"",mainActivity.isPasswordForget(),emailTemp);
    }

    @Override
    public void onClick(View v) {
        registerJson = getRegistrationJson();
        if(registerJson != null){
            checkPermission();
        }
    }



    private void syncRegistration(){
        String imeiNumber = ServiceUtil.getIMEINumber(mainActivity);
        registerJson.setImei(imeiNumber);
        boolean isNet = ServiceUtil.isNetworkConnected(mainActivity);
        if(isNet){
            progressDialog = ActivityUtil.showProgress(getString(R.string.reg_submit_heading), getString(R.string.reg_submit_message), mainActivity);
            mainActivity.getAccountService().createUser(registerJson,mainActivity);
        }else{
            ActivityUtil.showDialog(mainActivity, getString(R.string.no_network_heading), getString(R.string.no_network));
        }
    }


    @Subscribe
    public void processRegistrationResponse(ResponseData responseData){
       if(progressDialog != null){
           progressDialog.dismiss();
       }

        if(responseData.getStatusCode() == MessageConstant.REG_OK){
            mainActivityCallback.success(MessageConstant.REG_OK,null);
            return;
        }else if(responseData.getStatusCode() == MessageConstant.PROFILE_OK){
            loadData(responseData);
            return;
        }else if(responseData.getStatusCode() == MessageConstant.INVALID_MOBILE){
            name.requestFocus();
            return;
        }else {
            if(responseData.getData() != null){
                ActivityUtil.showDialog(mainActivity,getString(R.string.error),responseData.getData());
            }else{
                ActivityUtil.showDialog(mainActivity,getString(R.string.error),getString(R.string.internal_error));
            }

            return;
        }
    }

    private void loadData(ResponseData responseData){
        if(responseData.getStatusCode() == MessageConstant.PROFILE_OK){
            Gson gson = new Gson();
            String profileData = responseData.getData();
            RegisterJson registerJson =  gson.fromJson(profileData,RegisterJson.class);
            name.setText(registerJson.getName());
            email.setText(registerJson.getEmail());
            password.requestFocus();
        }else{
            name.requestFocus();
        }

    }

    private void loadData(){
        UserEntity userEntity = mainActivity.getAccountService().getUserDetails(mainActivity.getMobileNumber());
        if(userEntity != null){
            name.setText(userEntity.getName());
            email.setText(userEntity.getEmail());
            password.requestFocus();
        }else{
            name.requestFocus();
        }
    }


    @Override
    public void onPause() {
        MobilePayBus.getInstance().unregister(this);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        hideKeyboard();
        super.onDestroyView();
    }


    private void hideKeyboard(){
        ActivityUtil.hideKeyboard(mainActivity);
    }

    @Override
    public void onResume(){
        MobilePayBus.getInstance().register(this);
        MobilePayAnalytics.getInstance().trackScreenView("Registration -F Screen");
        super.onResume();
    }



    private void requestPermission(){
        requestPermissions(new String[] {Manifest.permission.READ_PHONE_STATE},
                REQUEST_CODE_ASK_PERMISSIONS);
    }


    private void checkPermission() {
        int hasWriteContactsPermission = ContextCompat.checkSelfPermission(mainActivity,
                Manifest.permission.READ_PHONE_STATE);
        if (hasWriteContactsPermission != PackageManager.PERMISSION_GRANTED) {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(mainActivity,Manifest.permission.READ_PHONE_STATE)) {
                showMessageOKCancel(getString(R.string.call_permission),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                requestPermission();
                            }
                        });
                return;
            }

            requestPermission();
            return;
        }else{
            syncRegistration();
        }

    }

    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(mainActivity)
                .setMessage(message)
                .setPositiveButton(getString(R.string.ok), okListener)
                .setNegativeButton(getString(R.string.cancel), null)
                .create()
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_PERMISSIONS:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission Granted
                    syncRegistration();
                } else {
                    // Permission Denied
                    Toast.makeText(mainActivity, getString(R.string.call_permission_denied), Toast.LENGTH_SHORT)
                            .show();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }


    public  interface MainActivityCallback {
        void success(int code,Object data);
    }
}
