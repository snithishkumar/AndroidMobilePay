package co.in.mobilepay.view.activities;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import co.in.mobilepay.R;
import co.in.mobilepay.sync.MobilePaySyncAdapter;
import co.in.mobilepay.service.AccountService;
import co.in.mobilepay.service.impl.AccountServiceImpl;
import co.in.mobilepay.service.impl.MessageConstant;
import co.in.mobilepay.view.fragments.FragmentsUtil;
import co.in.mobilepay.view.fragments.LoginFragment;
import co.in.mobilepay.view.fragments.MobileFragment;
import co.in.mobilepay.view.fragments.OtpFragment;
import co.in.mobilepay.view.fragments.RegistrationFragment;

public class MainActivity extends AppCompatActivity implements RegistrationFragment.MainActivityCallback,OtpFragment.MainActivityCallback,LoginFragment.MainActivityCallback,MobileFragment.MainActivityCallback{

    private AccountService accountService;

    RegistrationFragment registrationFragment = null;
    OtpFragment otpFragment = null;
    LoginFragment loginFragment = null;
    MobileFragment mobileFragment = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        init();
        setContentView(R.layout.activity_main);
        showFragment();
    }



    /**
     * Initialize service layer
     */
    private void init(){
        try{
            if(accountService == null){
                accountService = new AccountServiceImpl(this);
            }
           /* Account account = MobilePaySyncAdapter.getSyncAccount(this);
            ContentResolver.setIsSyncable(account,getString(R.string.auth_type),1);
            ContentResolver.setSyncAutomatically(account, getString(R.string.auth_type), true);
            ContentResolver.addPeriodicSync(account, getString(R.string.auth_type), Bundle.EMPTY, 60);*/

        }catch (Exception e){
            Log.e("Error","Error in init",e);
        }

    }

    private void showFragment(){
        boolean isUserPresent = accountService.isUserPresent();
        if(isUserPresent){
            loginFragment = new LoginFragment();
            FragmentsUtil.addFragment(this,loginFragment,R.id.main_container);
        }else{
            mobileFragment = new MobileFragment();
            FragmentsUtil.addFragment(this,mobileFragment,R.id.main_container);

        }
    }




    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    public AccountService getAccountService() {
        return accountService;
    }


    @Override
    public void success(int code, Object data) {

        switch (code){
            case MessageConstant.MOBILE_VERIFY_OK:
                otpFragment = new OtpFragment();
                Bundle bundle = new Bundle();
                bundle.putString("mobileNumber",(String)data);
                otpFragment.setArguments(bundle);
                FragmentsUtil.replaceFragment(this,otpFragment,R.id.main_container);
                break;
            case MessageConstant.REG_OK:
                loginFragment = new LoginFragment();
                FragmentsUtil.replaceFragment(this,loginFragment,R.id.main_container);
                break;
            case MessageConstant.OTP_OK:
                registrationFragment = new RegistrationFragment();
                 bundle = new Bundle();
                bundle.putString("mobileNumber",(String)data);
                registrationFragment.setArguments(bundle);
                FragmentsUtil.replaceFragment(this,registrationFragment,R.id.main_container);
                break;
            case MessageConstant.LOGIN_OK:
                Intent intent = new Intent(this, HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
                // Need to call home screen
                break;
            case MessageConstant.LOGIN_INVALID_MOBILE:
                mobileFragment = new MobileFragment();
                FragmentsUtil.addFragment(this,mobileFragment,R.id.main_container);
                break;
        }
    }
}
