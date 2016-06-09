package co.in.mobilepay.view.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.squareup.otto.Subscribe;

import co.in.mobilepay.R;
import co.in.mobilepay.json.request.RegisterJson;
import co.in.mobilepay.json.response.ResponseData;
import co.in.mobilepay.service.AccountService;
import co.in.mobilepay.service.impl.AccountServiceImpl;
import co.in.mobilepay.view.fragments.EditProfileFragment;
import co.in.mobilepay.view.fragments.FragmentsUtil;
import co.in.mobilepay.view.fragments.SaveCardsFragment;

/**
 * Created by Nithishkumar on 3/27/2016.
 */
public class NaviDrawerActivity extends AppCompatActivity implements EditProfileFragment.EditProfileFragmentCallBack{

    private AccountService accountService = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navi_drawer);
        init();
        int options = getIntent().getIntExtra("options",0);
        showFragment(options);
    }

    private void init(){
        try{
            accountService = new AccountServiceImpl(this);
        }catch (Exception e){
            Log.e("Error","Error in init - NaviDrawerActivity",e);
        }

    }

    private void showFragment(int options){
        switch (options){
            case 1:
               EditProfileFragment editProfileFragment = new EditProfileFragment();
                FragmentsUtil.addFragment(this, editProfileFragment, R.id.navi_drawer_container);
                break;

            case 3:
                SaveCardsFragment saveCardsFragment = new SaveCardsFragment();
                FragmentsUtil.addFragment(this, saveCardsFragment, R.id.navi_drawer_container);
                break;
        }
    }



    public void showNewCardFragment(View view){
        Intent intent = new Intent(this, NewSaveCardActivity.class);
        startActivity(intent);
    }

    @Override
    public void onSuccess(int option, RegisterJson registerJson) {
        if(option == 1){
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("registration",registerJson);
            intent.putExtra("isProfileUpdate",true);
           // intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }else {
            finish();
        }

    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // go to previous activity
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public AccountService getAccountService() {
        return accountService;
    }

}
