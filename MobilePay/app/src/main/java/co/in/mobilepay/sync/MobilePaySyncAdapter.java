package co.in.mobilepay.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.sql.SQLException;
import java.util.List;

import co.in.mobilepay.R;
import co.in.mobilepay.bus.MobilePayBus;
import co.in.mobilepay.bus.PurchaseListPoster;
import co.in.mobilepay.dao.PurchaseDao;
import co.in.mobilepay.dao.UserDao;
import co.in.mobilepay.dao.impl.PurchaseDaoImpl;
import co.in.mobilepay.dao.impl.UserDaoImpl;
import co.in.mobilepay.entity.AddressEntity;
import co.in.mobilepay.entity.DiscardEntity;
import co.in.mobilepay.entity.MerchantEntity;
import co.in.mobilepay.entity.PurchaseEntity;
import co.in.mobilepay.entity.UserEntity;
import co.in.mobilepay.json.response.AddressBookJson;
import co.in.mobilepay.json.response.AddressJson;
import co.in.mobilepay.json.response.DiscardJson;
import co.in.mobilepay.json.response.DiscardJsonList;
import co.in.mobilepay.json.response.LuggageJson;
import co.in.mobilepay.json.response.LuggagesListJson;
import co.in.mobilepay.json.response.PayedPurchaseDetailsJson;
import co.in.mobilepay.json.response.PayedPurchaseDetailsList;
import co.in.mobilepay.json.response.PurchaseJson;
import co.in.mobilepay.json.response.ResponseData;
import co.in.mobilepay.json.response.TokenJson;
import co.in.mobilepay.service.ServiceUtil;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Created by Nithish on 05-03-2016.
 */
public class MobilePaySyncAdapter extends AbstractThreadedSyncAdapter {


    public final String LOG_TAG = MobilePaySyncAdapter.class.getSimpleName();

    private PurchaseDao purchaseDao;
    private UserDao userDao;
    private MobilePayAPI mobilePayAPI;
    private Gson gson;

    public MobilePaySyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        init(context);

    }

    /**
     * Initialize DAO's and Gson
     * @param context
     */
    private void init(Context context){
        try{
            purchaseDao = new PurchaseDaoImpl(context);
            userDao = new UserDaoImpl(context);
            mobilePayAPI = ServiceAPI.INSTANCE.getMobilePayAPI();
            gson = new Gson();
        }catch (Exception e){
            Log.e(LOG_TAG,"Error in MobilePaySyncAdapter",e);
        }
    }

    /**
     * Common Sync Location. Call Corresponding Sync method based on flag
     * @param account
     * @param extras
     * @param authority
     * @param provider
     * @param syncResult
     */
    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        Log.i(LOG_TAG, "onPerformSync Called.");

        // 1 - Purchase List, 2- Order Status List, 3 - Purchase History List
       int currentTab =  extras.getInt("currentTab",0);
        switch (currentTab){
            case 1:
                syncUserDeliveryAddress();
                sendUnSyncPayedData();
                sendUnSyncDeclineData();
                syncPurchaseData();
                break;
            case 2:
                syncOrderStatus();
                break;
            case 3:
                syncPurchaseHistoryData();
                break;
        }

    }

    /**
     * Get Current User  and App Token
     * @return
     * @throws SQLException
     */
    private JsonObject userRequest()throws SQLException{
        UserEntity userEntity = userDao.getUser();
        JsonObject requestData = new JsonObject();
        requestData.addProperty("serverToken", userEntity.getServerToken());
        requestData.addProperty("accessToken", userEntity.getAccessToken());
        return requestData;
    }


    /**
     * Get Current User  and App Token
     * @return
     * @throws SQLException
     */
    private void userRequest(TokenJson tokenJson)throws SQLException{
        UserEntity userEntity = userDao.getUser();
        tokenJson.setAccessToken(userEntity.getAccessToken());
        tokenJson.setServerToken(userEntity.getServerToken());
    }


    /**
     * Get Current Purchase List from the server
     */
    private void syncPurchaseData(){
        try {
            // Get User and App Token
            JsonObject requestData =  userRequest();

            /**
             *  Get Most Recent Current Purchase Server time.Server send back based on this time.
             *  If time is -1 ,then server sends all the current purchase list.
             */
            long serverTime = purchaseDao.getMostRecentPurchaseServerTime();
            requestData.addProperty("serverTime",serverTime);
            // Sync Request
            Call<ResponseData> responseDataCall = mobilePayAPI.syncPurchaseData(requestData);
            // Server Response
            Response<ResponseData> dataResponse =  responseDataCall.execute();
            ResponseData responseData = dataResponse.body();

            int statusCode = responseData.getStatusCode();
            // Check the Status code, If its success or failure
            if(statusCode == 300){
                // Process Server Response
                String purchaseDetails = responseData.getData();
                List<PurchaseJson> purchaseJsonList =     gson.fromJson(purchaseDetails, new TypeToken<List<PurchaseJson>>() {
                }.getType());
                processPurchaseJson(purchaseJsonList);
                PurchaseListPoster purchaseListPoster = new PurchaseListPoster();
                purchaseListPoster.setStatusCode(200);
                MobilePayBus.getInstance().post(purchaseListPoster);
            }else{
                PurchaseListPoster purchaseListPoster = new PurchaseListPoster();
                purchaseListPoster.setStatusCode(500);
                MobilePayBus.getInstance().post(purchaseListPoster);
            }

        }catch (Exception e){
            Log.e("Error","Error in  syncPurchaseData",e);
            // -- TODO Need to Say Something wrong in mobile side
        }
    }




    /**
     * Process  PurchaseJson. It checks whether purchase data is already present or not. If it's present, it will update or it will create
     * @param purchaseJsonList
     *
     */
    private void processPurchaseJson(List<PurchaseJson> purchaseJsonList){
        //Save or update Each Purchase details
        for(PurchaseJson purchaseJson : purchaseJsonList){
            try{
                // Check given Merchant details present or not
                MerchantEntity  merchantEntity = purchaseDao.getMerchantEntity(purchaseJson.getMerchants().getMerchantUuid());
                // If its not present create New.Otherwise, it will update
                if(merchantEntity == null){
                    merchantEntity = new MerchantEntity(purchaseJson.getMerchants());
                    purchaseDao.createMerchantEntity(merchantEntity);
                }else if(merchantEntity.getLastModifiedDateTime() < purchaseJson.getMerchants().getLastModifiedDateTime()){
                    merchantEntity.toClone(purchaseJson.getMerchants());
                    purchaseDao.updateMerchantEntity(merchantEntity);
                    // Need to update
                }


                UserEntity dbUserEntity = userDao.getUser(purchaseJson.getUsers().getMobileNumber());
                //Check given Purchase details is already present or not.
                PurchaseEntity purchaseEntity = purchaseDao.getPurchaseEntity(purchaseJson.getPurchaseId());
                //If its not present, it will create new one. Otherwise, it will update
                if(purchaseEntity != null){
                    purchaseEntity.toClone(purchaseJson);
                    processAddressJson(purchaseJson, purchaseEntity);
                    processDiscardJson(purchaseJson,purchaseEntity);
                    purchaseDao.updatePurchase(purchaseEntity);
                }else{
                    purchaseEntity = new PurchaseEntity(purchaseJson);
                    purchaseEntity.setMerchantEntity(merchantEntity);
                    purchaseEntity.setUserEntity(dbUserEntity);
                    processAddressJson(purchaseJson, purchaseEntity);
                    processDiscardJson(purchaseJson,purchaseEntity);
                    purchaseDao.createPurchase(purchaseEntity);
                }
            }catch (Exception e){
                Log.e(LOG_TAG, "Error while processing purchase Details. Raw data["+purchaseJson+"]", e);
            }

        }

    }

    /**
     * Check that address is present in DB or not.
     * @param purchaseJson
     * @param purchaseEntity
     * @throws SQLException
     */
    private void processAddressJson(PurchaseJson purchaseJson,PurchaseEntity purchaseEntity)throws SQLException{
        // -- It's an rare scenario.
        AddressJson addressJson =  purchaseJson.getAddressJson();
        if(addressJson != null){
            AddressEntity addressEntity =  userDao.getAddressEntity(addressJson.getAddressUUID());
            if(addressEntity == null){
                addressEntity = new AddressEntity(addressJson);
                addressEntity.setIsSynced(true);
                userDao.saveAddress(addressEntity);
                purchaseEntity.setAddressEntity(addressEntity);
            }

        }
    }

    /**
     * Process Discard Details
     * @param purchaseJson
     * @param purchaseEntity
     * @throws SQLException
     */
    private void processDiscardJson(PurchaseJson purchaseJson,PurchaseEntity purchaseEntity)throws SQLException{
        DiscardJson discardJson =  purchaseJson.getDiscardJson();
        if(discardJson != null){
            DiscardEntity discardEntity = new DiscardEntity(discardJson);
            discardEntity.setPurchaseEntity(purchaseEntity);
            discardEntity.setCreatedDateTime(purchaseEntity.getLastModifiedDateTime());
            purchaseDao.createDiscardEntity(discardEntity);
        }
    }

    /**
     * Get Order status (NOT_YET_SHIPPING or PACKING or OUT_FOR_DELIVERY or Counter Id) from the server
     */
    private void syncOrderStatus(){
        try{
            // Get User and App Token
            JsonObject requestData =  userRequest();

            /**
             *  Get Most Recent  and First Luggage List Server time.Server send back based on this time.
             *  If time is -1 ,then server sends all the Luggage list.
             */
            long startTime = purchaseDao.getLeastLuggageServerTime();
            long endTime = purchaseDao.getMostRecentLuggageServerTime();
            requestData.addProperty("startTime",startTime);
            requestData.addProperty("endTime", endTime);
            // Sync Request
            Call<ResponseData> responseDataCall = mobilePayAPI.syncOrderStatus(requestData);
            // Server Response
            Response<ResponseData> dataResponse =  responseDataCall.execute();
            ResponseData responseData = dataResponse.body();

            int statusCode = responseData.getStatusCode();
            // Check the Status code, If its success or failure
            if(statusCode == 300){
                // Json to Object
                LuggagesListJson luggagesListJson =  gson.fromJson(responseData.getData(), LuggagesListJson.class);
                // Update Order Status only. Other details (Purchase data) are already present
                List<LuggageJson>  luggageJsonList =  luggagesListJson.getLuggageJsons();
                for(LuggageJson luggageJson : luggageJsonList){
                    //Check given Purchase details is already present or not.
                    PurchaseEntity purchaseEntity = purchaseDao.getPurchaseEntity(luggageJson.getPurchaseGuid());
                   // If purchaseEntity is Present, then update Order status (NOT_YET_SHIPPING or PACKING or OUT_FOR_DELIVERY or Counter Id)
                   if(purchaseEntity != null){
                       purchaseEntity.setLastModifiedDateTime(luggageJson.getUpdatedDateTime());
                       purchaseEntity.setServerDateTime(luggageJson.getServerDateTime());
                       purchaseEntity.setOrderStatus(luggageJson.getOrderStatus());
                       purchaseDao.updatePurchase(purchaseEntity);
                    }
                }
                List<PurchaseJson> purchaseJsonList =   luggagesListJson.getPurchaseJsons();
                processPurchaseJson(purchaseJsonList);
                PurchaseListPoster purchaseListPoster  = new PurchaseListPoster();
                purchaseListPoster.setStatusCode(200);
                MobilePayBus.getInstance().post(purchaseListPoster);
                // -- TODO Need to send notification to list view
            }else{
                PurchaseListPoster purchaseListPoster  = new PurchaseListPoster();
                purchaseListPoster.setStatusCode(500);
                MobilePayBus.getInstance().post(purchaseListPoster);
            }

        }catch (Exception e){
            Log.e(LOG_TAG,"Error in syncOrderStatus",e);
            // -- TODO Need to Say Something wrong in mobile side
        }
    }


    /**
     * Get Current Purchase List from the server
     */
    private void syncPurchaseHistoryData(){
        try {
            // Get User and App Token
            JsonObject requestData =  userRequest();

            /**
             *  Get Most Recent Purchase history Server time.Server send back based on this time.
             *  If time is -1 ,then server sends all the current purchase list.
             */
            long serverTime = purchaseDao.getRecentPurchaseHisServerTime();
            requestData.addProperty("serverTime", serverTime);
            // Sync Request
            Call<ResponseData> responseDataCall = mobilePayAPI.syncPurchaseHistoryData(requestData);
            // Server Response
            Response<ResponseData> dataResponse =  responseDataCall.execute();
            ResponseData responseData = dataResponse.body();

            int statusCode = responseData.getStatusCode();
            // Check the Status code, If its success or failure
            if(statusCode == 300){
                // Process Server Response
                String purchaseDetails = responseData.getData();
                List<PurchaseJson> purchaseJsonList = gson.fromJson(purchaseDetails, new TypeToken<List<PurchaseJson>>() {
                }.getType());
                processPurchaseJson(purchaseJsonList);
                // -- TODO Need to send notification to list view
            }else{
                // -- TODO Need to Say Something wrong in server
            }

        }catch (Exception e){
            Log.e("Error","Error in  syncPurchaseData",e);
            // -- TODO Need to Say Something wrong in mobile side
        }
    }


    /**
     * Sync User Delivery Address
     */
    private void syncUserDeliveryAddress(){
        try{
            // Send Un synced Address
            List<AddressEntity> addressEntityList =  userDao.getUnSyncedAddress();

            // No need call sync bcs address book is empty
            if(addressEntityList.size() < 1){
                return;
            }

            AddressBookJson requestAddressBook = new AddressBookJson();
            // Get User and App Token
            userRequest(requestAddressBook);
            // Get Most Recent Synced Delivery address
            long lastModifiedTime =   userDao.getLastModifiedTime();
            requestAddressBook.setLastModifiedTime(lastModifiedTime);

            for(AddressEntity addressEntity : addressEntityList){
                AddressJson addressJson = new AddressJson(addressEntity);
                requestAddressBook.getAddressList().add(addressJson);
            }

            // Sync Request
            Call<ResponseData> responseDataCall =  mobilePayAPI.syncUserDeliveryAddress(requestAddressBook);

            // Server Response
            Response<ResponseData> dataResponse =  responseDataCall.execute();
            ResponseData responseData = dataResponse.body();

            int statusCode = responseData.getStatusCode();

            // Check the Status code, If its success or failure
            if(statusCode == 200){
                // Process Server Response
                String addressDetails = responseData.getData();
                // Json to Object
                AddressBookJson addressBookJson =  gson.fromJson(addressDetails, AddressBookJson.class);
                List<AddressJson> addressList  =addressBookJson.getAddressList();
                /**
                 * Check address is already present in DB or not. If not present, then create new record. Suppose, if its present then check last modified time
                 */
                for(AddressJson addressJson : addressList){
                   AddressEntity dbAddressEntity =  userDao.getAddressEntity(addressJson.getAddressUUID());
                    if(dbAddressEntity == null){
                        dbAddressEntity = new AddressEntity(addressJson);
                        dbAddressEntity.setAddressUUID(ServiceUtil.generateUUID());
                        userDao.saveAddress(dbAddressEntity);
                    }else if(dbAddressEntity.getLastModifiedTime() < addressJson.getLastModifiedTime()){
                        dbAddressEntity.toAddressEntity(addressJson);
                        userDao.updateAddress(dbAddressEntity);
                    }
                }

            }

        }catch (Exception e){
            Log.e(LOG_TAG,"Error in getUserDeliveryAddress",e);
        }

    }

// -- TODO Address Edit. Need to handle in server side
    public void sendUnSyncPayedData(){
        try{
            // Get UnSynced Payed Data
            List<PurchaseEntity> purchaseEntityList = purchaseDao.getUnSyncedPayedEntity();

            // No need call sync bcs PurchaseEntity is empty
            if(purchaseEntityList.size() < 1){
                return;
            }

            PayedPurchaseDetailsList payedPurchaseDetailsList = new PayedPurchaseDetailsList();
            // Entity to Json
            for(PurchaseEntity purchaseEntity : purchaseEntityList){
                PayedPurchaseDetailsJson payedPurchaseDetailsJson = new PayedPurchaseDetailsJson(purchaseEntity);
                payedPurchaseDetailsList.getPurchaseDetailsJsons().add(payedPurchaseDetailsJson);
            }

            userRequest(payedPurchaseDetailsList);
            // Sync Request
            Call<ResponseData> responseDataCall = mobilePayAPI.syncPayedData(payedPurchaseDetailsList);
            // Server Response
            Response<ResponseData> dataResponse = responseDataCall.execute();
            ResponseData responseData = dataResponse.body();
            // Success Response
            int statusCode = responseData.getStatusCode();
            if (statusCode == 200) {
                // Update ServerSync Time and IsSync Flag
                String response = responseData.getData();
                List<PurchaseJson> purchaseJsons = gson.fromJson(response, new TypeToken<List<PurchaseJson>>() {
                }.getType());
                purchaseDao.updateServerSyncTime(purchaseJsons);

            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "Error in getUserDeliveryAddress", e);
        }
    }


    /**
     * Send UnSynced Declined Data to the server
     */
    public void sendUnSyncDeclineData() {
        try {
            List<PurchaseEntity> purchaseEntityList = purchaseDao.getUnSyncedDiscardEntity();
            DiscardJsonList discardJsonList = new DiscardJsonList();
            boolean isData = false;
            for (PurchaseEntity purchaseEntity : purchaseEntityList) {
                DiscardEntity discardEntity = purchaseDao.getDiscardEntity(purchaseEntity);
                DiscardJson discardJson = new DiscardJson(discardEntity, purchaseEntity);
                discardJsonList.getDiscardJsons().add(discardJson);
                isData = true;
            }
            if (isData) {
                userRequest(discardJsonList);
                // Sync Request
                Call<ResponseData> responseDataCall = mobilePayAPI.syncDiscardData(discardJsonList);
                // Server Response
                Response<ResponseData> dataResponse = responseDataCall.execute();
                ResponseData responseData = dataResponse.body();
                // Success Response
                int statusCode = responseData.getStatusCode();
                if (statusCode == 200) {
                    // Update ServerSync Time and IsSync Flag
                    String response = responseData.getData();
                    List<PurchaseJson> purchaseJsons = gson.fromJson(response, new TypeToken<List<PurchaseJson>>() {
                    }.getType());
                    purchaseDao.updateServerSyncTime(purchaseJsons);

                }
            }


        } catch (Exception e) {
            Log.e(LOG_TAG, "Error in getUserDeliveryAddress", e);
        }
    }


    /**
     * Helper method to get the fake account to be used with SyncAdapter, or make a new one
     * if the fake account doesn't exist yet.  If we make a new account, we call the
     * onAccountCreated method so we can initialize things.
     *
     * @param context The context used to access the account service
     * @return a fake account.
     */
    public static Account getSyncAccount(Context context) {
        // Get an instance of the Android account manager
        AccountManager accountManager =
                (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);

        // Create the account type and default account
        Account newAccount = new Account(
                context.getString(R.string.app_name), context.getString(R.string.auth_type));

        // If the password doesn't exist, the account doesn't exist
        if ( null == accountManager.getPassword(newAccount) ) {

        /*
         * Add the account and account type, no password or user data
         * If successful, return the Account object, otherwise report an error.
         */
            if (!accountManager.addAccountExplicitly(newAccount, "", null)) {
                return null;
            }
            /*
             * If you don't set android:syncable="true" in
             * in your <provider> element in the manifest,
             * then call ContentResolver.setIsSyncable(account, AUTHORITY, 1)
             * here.
             */

        }
        return newAccount;
    }
}
