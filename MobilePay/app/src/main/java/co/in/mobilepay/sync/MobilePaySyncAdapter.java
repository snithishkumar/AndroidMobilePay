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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import co.in.mobilepay.R;
import co.in.mobilepay.application.MobilePayAnalytics;
import co.in.mobilepay.bus.MobilePayBus;
import co.in.mobilepay.bus.PurchaseListPoster;
import co.in.mobilepay.dao.PurchaseDao;
import co.in.mobilepay.dao.UserDao;
import co.in.mobilepay.dao.impl.PurchaseDaoImpl;
import co.in.mobilepay.dao.impl.UserDaoImpl;
import co.in.mobilepay.entity.AddressEntity;
import co.in.mobilepay.entity.CounterDetailsEntity;
import co.in.mobilepay.entity.DiscardEntity;
import co.in.mobilepay.entity.HomeDeliveryOptionsEntity;
import co.in.mobilepay.entity.MerchantEntity;
import co.in.mobilepay.entity.PurchaseEntity;
import co.in.mobilepay.entity.TransactionalDetailsEntity;
import co.in.mobilepay.entity.UserEntity;
import co.in.mobilepay.enumeration.GsonAPI;
import co.in.mobilepay.enumeration.OrderStatus;
import co.in.mobilepay.json.request.RegisterJson;
import co.in.mobilepay.json.response.AddressBookJson;
import co.in.mobilepay.json.response.AddressJson;
import co.in.mobilepay.json.response.CalculatedAmounts;
import co.in.mobilepay.json.response.CounterDetailsJson;
import co.in.mobilepay.json.response.DiscardJson;
import co.in.mobilepay.json.response.DiscardJsonList;
import co.in.mobilepay.json.response.GetPurchaseDetailsList;
import co.in.mobilepay.json.response.LuggageJson;
import co.in.mobilepay.json.response.OrderStatusJsonsList;
import co.in.mobilepay.json.response.PayedPurchaseDetailsJson;
import co.in.mobilepay.json.response.PayedPurchaseDetailsList;
import co.in.mobilepay.json.response.PurchaseJson;
import co.in.mobilepay.json.response.ResponseData;
import co.in.mobilepay.json.response.TokenJson;
import co.in.mobilepay.service.ServiceUtil;
import co.in.mobilepay.service.impl.MessageConstant;
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
    private SyncAccountDetails syncAccountDetails;

    ExecutorService executorService = Executors.newFixedThreadPool(5);
    CountDownLatch countDownLatch = new CountDownLatch(4);

private boolean isLoginFailed = false;

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
            gson =GsonAPI.INSTANCE.getGson();

        }catch (Exception e){
            MobilePayAnalytics.getInstance().trackException(e,"Error in init - MobilePaySyncAdapter");
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
       try{

           int currentScreen =  extras.getInt("currentScreen",0);
               if(currentScreen > 0){
               initSyncAccountDetails();
               switch (currentScreen){
                   case MessageConstant.MOBILE:
                       String  mobileNumber =  extras.getString("mobileNumber");
                       ResponseData requestOtpResponseData =   syncAccountDetails.requestOtp(mobileNumber);
                       MobilePayBus.getInstance().post(requestOtpResponseData);
                       break;

                   case MessageConstant.OTP:
                       mobileNumber =  extras.getString("mobileNumber");
                       String otpPassword =  extras.getString("otpPassword");
                       String registerJson =  extras.getString("registration");
                       if(registerJson != null){
                           RegisterJson registerJsonObject = gson.fromJson(registerJson,RegisterJson.class);
                           ResponseData requestValidateOtpResponseData =   syncAccountDetails.validateOtp(otpPassword,registerJsonObject);
                           MobilePayBus.getInstance().post(requestValidateOtpResponseData);
                       }else{
                           ResponseData requestValidateOtpResponseData =   syncAccountDetails.validateOtp(otpPassword,mobileNumber);
                           MobilePayBus.getInstance().post(requestValidateOtpResponseData);
                       }
                       break;

                   case MessageConstant.REGISTER:
                       registerJson =  extras.getString("registration");
                       RegisterJson registerJsonObject = gson.fromJson(registerJson,RegisterJson.class);
                       ResponseData registrationResponseData =   syncAccountDetails.userRegistration(registerJsonObject);
                       MobilePayBus.getInstance().post(registrationResponseData);
                       break;

                   case MessageConstant.PROFILE:
                       ResponseData userProfileResponse =   syncAccountDetails.getUserProfile();
                       MobilePayBus.getInstance().post(userProfileResponse);
                       break;

                   case MessageConstant.REGISTER_PROF_DATA:
                       mobileNumber =  extras.getString("mobileNumber");
                       ResponseData userProfileResponseData =   syncAccountDetails.getUserProfile(mobileNumber);
                       MobilePayBus.getInstance().post(userProfileResponseData);
                       break;

                   case MessageConstant.SYNC_DATA:
                       sendUnSyncedDataSynchronize();
                       break;

               }
           }else{
                   isLoginFailed = false;
               countDownLatch = new CountDownLatch(4);
               executorService.execute(new Runnable() {
                   @Override
                   public void run() {
                       sendUnSyncedDataSynchronize();
                       countDownLatch.countDown();
                   }
               });

               executorService.execute(new Runnable() {
                   @Override
                   public void run() {
                       syncPurchaseData();
                       countDownLatch.countDown();
                   }
               });

               executorService.execute(new Runnable() {
                   @Override
                   public void run() {
                       syncOrderStatus();
                       countDownLatch.countDown();
                   }
               });

               executorService.execute(new Runnable() {
                   @Override
                   public void run() {
                       syncPurchaseHistoryData();
                       countDownLatch.countDown();
                   }
               });
               countDownLatch.await();
                   if(isLoginFailed){
                       ResponseData responseData = new ResponseData();
                       responseData.setStatusCode(401);
                       MobilePayBus.getInstance().post(responseData);
                   }
           }


       }catch (Exception e){
           Log.e(LOG_TAG,"Error in onPerformSync",e);
           MobilePayAnalytics.getInstance().trackException(e,"Error in onPerformSync - MobilePaySyncAdapter");
       }


    }


    private void initSyncAccountDetails(){
        if(syncAccountDetails == null){
            syncAccountDetails = new SyncAccountDetails(getContext());
        }

    }



    /**
     * Send UnSynced Data to the server
     */
    private void sendUnSyncedDataSynchronize(){
        syncUserDeliveryAddress();
        sendUnSyncDeclineData();
        sendUnSyncPayedData();
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
        ResponseData responseData = null;
        try {
            // Get User and App Token
            TokenJson tokenJson = new TokenJson();
            userRequest(tokenJson);

            // Sync Request. It Returns Current Purchase UUIDs
            Call<ResponseData> responseDataCall = mobilePayAPI.syncPurchaseListFromServer();

            // Server Response
            Response<ResponseData> dataResponse =  responseDataCall.execute();
            int responseCode = dataResponse.code();
            if(responseCode == 401){
                isLoginFailed = true;
                return;
            }else if(responseCode == 200){
                responseData = dataResponse.body();

                int statusCode = responseData.getStatusCode();
                // Check the Status code, If its success or failure
                if(statusCode == 300){
                    // Process Server Response
                    String purchaseUUIDs = responseData.getData();
                    // Convert UUIDs to List
                    List<String> purchaseJsonList =     gson.fromJson(purchaseUUIDs, new TypeToken<List<String>>() {
                    }.getType());
                    // Process Each UUIDs
                    processPurchaseUUIDs(purchaseJsonList);
                    // Once Process, Completed Need to Update List View
                    PurchaseListPoster purchaseListPoster = new PurchaseListPoster();
                    purchaseListPoster.setStatusCode(200);
                    // Post Success message to List View
                    MobilePayBus.getInstance().post(purchaseListPoster);
                    return;
                }else{
                    // In the case of failure, Send Failure message to the server.
                    postErrorCode(statusCode);
                }
            }else{
                // Need to log the Error -- TODO
            }


        }catch (Exception e){
            Log.e("Error","Error in  syncPurchaseListFromServer,responseData["+responseData+"]",e);
           MobilePayAnalytics.getInstance().trackException(e,"Error in  syncPurchaseListFromServer,responseData["+responseData+"]");
        }
        //Send Error as Internal Error
        postErrorCode(MessageConstant.REG_ERROR_CODE);
    }

    /**
     * Post Error Message
     * @param errorCode
     */
    private void postErrorCode(int errorCode){
        PurchaseListPoster purchaseListPoster = new PurchaseListPoster();
        purchaseListPoster.setStatusCode(errorCode);
        MobilePayBus.getInstance().post(purchaseListPoster);
    }


    /**
     * Check given purchaseUUIDs with Database. If its not present, then download purchase Details. And also, PurchaseUUID present in local db and that is not in server. Then need to get updates for that PurchaseUUID
     * @param purchaseUUIDs
     * @throws Exception
     */
    private void processPurchaseUUIDs(List<String> purchaseUUIDs)throws  Exception{
       if(purchaseUUIDs.size() > 0){
           // Get Purchase UUID from Database
           List<String> dbPurchaseUUIDs = purchaseDao.getPurchaseUUIDs();
           if(dbPurchaseUUIDs.size() > 0){
               List<String> purchaseJsonListTemp = new ArrayList<>(purchaseUUIDs);
              // Need to Download
               purchaseUUIDs.removeAll(dbPurchaseUUIDs);
               // Need to Get Update.
               dbPurchaseUUIDs.removeAll(purchaseJsonListTemp);
               // If dbPurchaseUUIDs contains data. We must get updates
               if(dbPurchaseUUIDs.size() > 0){
                   // Get Purchase Details
                   syncPurchaseDetailsList(dbPurchaseUUIDs);
               }

           }
           // Get Purchase Details. It process one by one. Bsc to process slow internet
           if(purchaseUUIDs.size() > 0){
               syncPurchaseDetails(purchaseUUIDs);
           }


       }

    }

    /**
     * Get Purchase Details. It process one by one
     * @param purchaseUUIDs
     * @throws SQLException
     */
    private void syncPurchaseDetails(List<String> purchaseUUIDs)throws SQLException{
       List<String> nonSyncPurchaseUUIDs = purchaseDao.getPurchaseUUIDs(purchaseUUIDs);
        if(nonSyncPurchaseUUIDs.size() > 0){
            purchaseUUIDs.removeAll(nonSyncPurchaseUUIDs);
        }
        GetPurchaseDetailsList getPurchaseDetailsList = new GetPurchaseDetailsList();
        userRequest(getPurchaseDetailsList);
        do{
            ResponseData responseData = null;
            try{
                getPurchaseDetailsList.getPurchaseUUIDs().clear();
                getPurchaseDetailsList.getPurchaseUUIDs().add(purchaseUUIDs.get(0));
                purchaseUUIDs.remove(0);
                Call<ResponseData> responseDataCall =   mobilePayAPI.syncPurchaseDetailsData(getPurchaseDetailsList);

                // Server Response
                Response<ResponseData> dataResponse =  responseDataCall.execute();
                 responseData = dataResponse.body();

                int statusCode = responseData.getStatusCode();
                // Check the Status code, If its success or failure
                if(statusCode == 300){
                    String purchaseDetails = responseData.getData();
                    List<PurchaseJson> purchaseJsonList =     gson.fromJson(purchaseDetails, new TypeToken<List<PurchaseJson>>() {
                    }.getType());
                    processPurchaseJson(purchaseJsonList);
                }

            }catch (Exception e){
                MobilePayAnalytics.getInstance().trackException(e,"Error in  syncPurchaseDetails["+getPurchaseDetailsList+"],ResponseData["+responseData+"]");
                Log.e(LOG_TAG,"Error in syncPurchaseDetails",e);
            }


        }while (purchaseUUIDs.size() > 0);
    }

    /**
     * Get Purchase Details. It sends all UUIDs once
     * @param purchaseUUIDs
     * @throws SQLException
     */
    private void syncPurchaseDetailsList(List<String> purchaseUUIDs)throws Exception{
        GetPurchaseDetailsList getPurchaseDetailsList = new GetPurchaseDetailsList();
        // Get User Token
        userRequest(getPurchaseDetailsList);

        getPurchaseDetailsList.getPurchaseUUIDs().addAll(purchaseUUIDs);
        // Sync Request
        Call<ResponseData> responseDataCall =   mobilePayAPI.syncPurchaseDetailsData(getPurchaseDetailsList);

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
            // Process Purchase Details Json
            processPurchaseJson(purchaseJsonList);
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
                    if(purchaseEntity.getServerDateTime() < purchaseJson.getServerDateTime() && purchaseEntity.isSync()){
                        purchaseEntity.toClone(purchaseJson);
                        processCalculatedAmounts(purchaseJson,purchaseEntity);
                        processAddressJson(purchaseJson, purchaseEntity);
                        processDiscardJson(purchaseJson,purchaseEntity);
                        purchaseDao.updatePurchase(purchaseEntity);
                    }

                }else{
                    purchaseEntity = new PurchaseEntity(purchaseJson);
                    purchaseEntity.setMerchantEntity(merchantEntity);
                    purchaseEntity.setUserEntity(dbUserEntity);
                    processCalculatedAmounts(purchaseJson,purchaseEntity);
                    purchaseDao.createPurchase(purchaseEntity);
                    processAddressJson(purchaseJson, purchaseEntity);
                    processDiscardJson(purchaseJson,purchaseEntity);
                    processHomeDeliveryJson(purchaseJson,purchaseEntity);
                }
                createCounterDetails(purchaseJson.getCounterDetails(),purchaseEntity);
            }catch (Exception e){
                MobilePayAnalytics.getInstance().trackException(e,"Error while processing purchase Details. Raw data["+purchaseJson+"]");
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
                addressEntity.setSynced(true);
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
     * Process Home Delivery Options
     * @param purchaseJson
     * @param purchaseEntity
     * @throws SQLException
     */
    private  void processHomeDeliveryJson(PurchaseJson purchaseJson,PurchaseEntity purchaseEntity)throws  SQLException{
        HomeDeliveryOptionsEntity homeDeliveryOptionsEntity = purchaseJson.getHomeDeliveryOptions();
        if(homeDeliveryOptionsEntity != null){
            homeDeliveryOptionsEntity.setPurchaseEntity(purchaseEntity);
            purchaseDao.createHomeDeliveryOptions(homeDeliveryOptionsEntity);
        }
    }


    /**
     * Process Calculated Amount Details
     * @param purchaseJson
     * @param purchaseEntity
     * @throws SQLException
     */
    private void processCalculatedAmounts(PurchaseJson purchaseJson,PurchaseEntity purchaseEntity)throws  SQLException{
        CalculatedAmounts calculatedAmounts = purchaseJson.getCalculatedAmounts();
        if(calculatedAmounts != null){
            purchaseEntity.setCalculatedAmountDetails(gson.toJson(calculatedAmounts));
            purchaseEntity.setTotalAmount(calculatedAmounts.getTotalAmount());
        }else{
            purchaseEntity.setTotalAmount(purchaseJson.getTotalAmount());
        }
    }

    /**
     * Get Order status (NOT_YET_SHIPPING or PACKING or OUT_FOR_DELIVERY or Counter Id) from the server
     */
    private void syncOrderStatus(){
        ResponseData responseData = null;
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
            int httpStatusCode = dataResponse.code();
            if(httpStatusCode == 200){
                responseData = dataResponse.body();

                // If any purchase UUIDs missed in local Database. Then need to get full details
                List<String> purchaseUUIDs = new ArrayList<>();

                int statusCode = responseData.getStatusCode();
                // Check the Status code, If its success or failure
                if(statusCode == 300){
                    // Json to Object
                    OrderStatusJsonsList orderStatusJsonsList =  gson.fromJson(responseData.getData(), OrderStatusJsonsList.class);
                    // Update Order Status only. Other details (Purchase data) are already present
                    List<LuggageJson>  luggageJsonList =  orderStatusJsonsList.getLuggageJsons();

                    for(LuggageJson luggageJson : luggageJsonList){
                        //Check given Purchase details is already present or not.
                        PurchaseEntity purchaseEntity = purchaseDao.getPurchaseEntity(luggageJson.getPurchaseGuid());
                        // If purchaseEntity is Present, then update Order status (NOT_YET_SHIPPING or PACKING or OUT_FOR_DELIVERY or Counter Id)
                        if(purchaseEntity != null){
                            purchaseEntity.setLastModifiedDateTime(luggageJson.getUpdatedDateTime());
                            purchaseEntity.setServerDateTime(luggageJson.getServerDateTime());
                            purchaseEntity.setOrderStatus(luggageJson.getOrderStatus());
                            purchaseDao.updatePurchase(purchaseEntity);

                     /*  NotificationEntity notificationEntity = notificationDao.getNotificationEntity(purchaseEntity.getPurchaseGuid());
                       if(notificationEntity != null){
                           notificationEntity.setNotificationType(NotificationType.STATUS);
                           notificationDao.updateNotification(notificationEntity);
                       }*/

                        }else{ // If purchaseEntity is not present, then need to get purchase details data
                            purchaseUUIDs.add(luggageJson.getPurchaseGuid());
                        }
                        // Process Counter Details
                        if(luggageJson.getCounterDetails() != null){
                            createCounterDetails(luggageJson.getCounterDetails(),purchaseEntity);
                        }

                    }
                    // If purchaseEntity is not present, then Create New Purchase Record
                    List<PurchaseJson> purchaseJsonList =   orderStatusJsonsList.getPurchaseJsons();
                    processPurchaseJson(purchaseJsonList);
                    // Sometimes, There may be possible, PurchaseUUIDs missed in between order status time. So we need to process that missed data.It it not possible, but we need to do
                    if(purchaseUUIDs.size() > 0){
                        syncPurchaseDetailsList(purchaseUUIDs);
                    }

                    // Once Process, Completed Need to Update List View
                    PurchaseListPoster purchaseListPoster  = new PurchaseListPoster();
                    purchaseListPoster.setStatusCode(200);
                    // Success Post
                    MobilePayBus.getInstance().post(purchaseListPoster);
                    return;

                }else{  // Error Post
                    postErrorCode(statusCode);
                }
            }else if(httpStatusCode == 401){
                isLoginFailed = true;
                return;
            }else {

            }


        }catch (Exception e){
            MobilePayAnalytics.getInstance().trackException(e,"Error in syncOrderStatus,Raw Data["+responseData+"]");
            Log.e(LOG_TAG,"Error in syncOrderStatus",e);
            // -- TODO Need to Say Something wrong in mobile side
        }
        // Error Post
        postErrorCode(MessageConstant.REG_ERROR_CODE);
    }


    /*
     * Create Counter Details Entityii
     * @param counterDetails
     * @param purchaseEntity
     * @throws SQLException
     */
    private void createCounterDetails(CounterDetailsJson counterDetails,PurchaseEntity purchaseEntity)throws SQLException{
        if(purchaseEntity.getOrderStatus().toString().equals(OrderStatus.READY_TO_COLLECT.toString())){
            CounterDetailsEntity counterDetailsEntity = purchaseDao.getCounterDetailsEntity(purchaseEntity);
            if(counterDetailsEntity == null){
                counterDetailsEntity = new CounterDetailsEntity(counterDetails);
                counterDetailsEntity.setPurchaseEntity(purchaseEntity);
                purchaseDao.createCounterDetails(counterDetailsEntity);
            }else{
                counterDetailsEntity.setCounterNumber(counterDetails.getCounterNumber());
                counterDetailsEntity.setMessage(counterDetails.getMessage());
                counterDetailsEntity.setPurchaseEntity(purchaseEntity);
                purchaseDao.updateCounterDetails(counterDetailsEntity);
            }
        }
    }


    /**
     * Get Current Purchase List from the server
     */
    private void syncPurchaseHistoryData(){
        ResponseData responseData = null;
        try {
            // Get User and App Token
         TokenJson tokenJson = new TokenJson();
            userRequest(tokenJson);


            // Sync Request
            Call<ResponseData> responseDataCall = mobilePayAPI.syncPurchaseHistoryList();
            // Server Response
            Response<ResponseData> dataResponse =  responseDataCall.execute();
            int httpStatusCode = dataResponse.code();
            if(httpStatusCode == 200){
                 responseData = dataResponse.body();

                int statusCode = responseData.getStatusCode();
                // Check the Status code, If its success or failure
                if(statusCode == 300){
                    // Process Server Response
                    String purchaseDetails = responseData.getData();
                    List<String> purchaseHistoryList = gson.fromJson(purchaseDetails, new TypeToken<List<String>>() {
                    }.getType());

                    // Get PurchaseHistory UUID from Database
                    List<String> dBPurchaseHistoryUUIDs = purchaseDao.getPurchaseHistoryUUIDs();

                    if(dBPurchaseHistoryUUIDs.size() > 0){
                        // Need to Download
                        purchaseHistoryList.removeAll(dBPurchaseHistoryUUIDs);


                    }
                    if(purchaseHistoryList.size() > 0){
                        syncPurchaseDetailsList(purchaseHistoryList);
                    }


                    // Once Process, Completed Need to Update List View
                    PurchaseListPoster purchaseListPoster  = new PurchaseListPoster();
                    purchaseListPoster.setStatusCode(200);
                    // Success Post
                    MobilePayBus.getInstance().post(purchaseListPoster);
                    return;
                }else{
                    postErrorCode(statusCode);
                }
            }else if(httpStatusCode == 401){
                isLoginFailed = true;
                return;
            }else {

            }


        }catch (Exception e){
            MobilePayAnalytics.getInstance().trackException(e,"Error in syncPurchaseListFromServer,Raw Data["+responseData+"]");
            Log.e(LOG_TAG,"Error in  syncPurchaseListFromServer",e);
        }
        // Error Post
        postErrorCode(MessageConstant.REG_ERROR_CODE);
    }





    /**
     * Sync User Delivery Address
     */
    private void syncUserDeliveryAddress(){
        ResponseData responseData = null;
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
            int httpResponseCode = dataResponse.code();
            if(httpResponseCode == 401){
                isLoginFailed = true;
                return;
            }else if(httpResponseCode == 200){
                 responseData = dataResponse.body();

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
            }else{

            }



        }catch (Exception e){
            MobilePayAnalytics.getInstance().trackException(e,"Error in syncUserDeliveryAddress,Raw Data["+responseData+"]");
            Log.e(LOG_TAG,"Error in syncUserDeliveryAddress",e);
        }

    }

// -- TODO Address Edit. Need to handle in server side
    public void sendUnSyncPayedData(){
        List<PurchaseEntity> purchaseEntityList = null;
        ResponseData responseData = null;
        try{
            // Get UnSynced Payed Data
            purchaseEntityList = purchaseDao.getUnSyncedPayedEntity();

            // No need call sync bcs PurchaseEntity is empty
            if(purchaseEntityList.size() < 1){
                return;
            }

            PayedPurchaseDetailsList payedPurchaseDetailsList = new PayedPurchaseDetailsList();
            // Entity to Json
            for(PurchaseEntity purchaseEntity : purchaseEntityList){
                PayedPurchaseDetailsJson payedPurchaseDetailsJson = new PayedPurchaseDetailsJson(purchaseEntity);
                CalculatedAmounts calculatedAmounts = gson.fromJson(purchaseEntity.getCalculatedAmountDetails(),CalculatedAmounts.class);
                payedPurchaseDetailsJson.setCalculatedAmounts(calculatedAmounts);
                List<TransactionalDetailsEntity>  entityList = getTransactionDetails(purchaseEntity);
                payedPurchaseDetailsJson.getTransactions().addAll(entityList);
                payedPurchaseDetailsList.getPurchaseDetailsJsons().add(payedPurchaseDetailsJson);
            }

            userRequest(payedPurchaseDetailsList);
            // Sync Request
            Call<ResponseData> responseDataCall = mobilePayAPI.syncPayedData(payedPurchaseDetailsList);
            // Server Response
            Response<ResponseData> dataResponse = responseDataCall.execute();
            int httpStatusCode = dataResponse.code();
            if(httpStatusCode == 200){
                responseData = dataResponse.body();
                // Success Response
                int statusCode = responseData.getStatusCode();
                if (statusCode == 200) {
                    // Update ServerSync Time and IsSync Flag
                    String response = responseData.getData();
                    List<PurchaseJson> purchaseJsons = gson.fromJson(response, new TypeToken<List<PurchaseJson>>() {
                    }.getType());
                    purchaseDao.updateServerSyncTime(purchaseJsons);

                }
            }else if(httpStatusCode == 401){
                isLoginFailed = true;
                return;
            }else{

            }


        } catch (Exception e) {
            MobilePayAnalytics.getInstance().trackException(e,"Error in sendUnSyncPayedData,Raw data :purchaseEntityList["+purchaseEntityList+"],responseData["+responseData+"]");
            Log.e(LOG_TAG, "Error in sendUnSyncPayedData", e);
        }
    }


    /**
     * Send UnSynced Declined Data to the server
     */
    public void sendUnSyncDeclineData() {
        List<PurchaseEntity> purchaseEntityList = null;
        ResponseData responseData = null;
        try {
            purchaseEntityList = purchaseDao.getUnSyncedDiscardEntity();
            DiscardJsonList discardJsonList = new DiscardJsonList();
            boolean isData = false;
            for (PurchaseEntity purchaseEntity : purchaseEntityList) {
                DiscardEntity discardEntity = purchaseDao.getDiscardEntity(purchaseEntity);

                DiscardJson discardJson = new DiscardJson(discardEntity, purchaseEntity);
                String calculatedAmtsJson = purchaseEntity.getCalculatedAmountDetails();
                if(calculatedAmtsJson != null){
                    CalculatedAmounts calculatedAmounts =  gson.fromJson(calculatedAmtsJson,CalculatedAmounts.class);
                    discardJson.setCalculatedAmounts(calculatedAmounts);
                }
                List<TransactionalDetailsEntity>  entityList = getTransactionDetails(purchaseEntity);
                discardJson.getTransactions().addAll(entityList);
                discardJsonList.getDiscardJsons().add(discardJson);
                isData = true;
            }
            if (isData) {
                userRequest(discardJsonList);
                // Sync Request
                Call<ResponseData> responseDataCall = mobilePayAPI.syncDiscardData(discardJsonList);
                // Server Response
                Response<ResponseData> dataResponse = responseDataCall.execute();
                int httpStatusCode = dataResponse.code();
                if(httpStatusCode == 200){
                    responseData = dataResponse.body();
                    // Success Response
                    int statusCode = responseData.getStatusCode();
                    if (statusCode == 200) {
                        // Update ServerSync Time and IsSync Flag
                        String response = responseData.getData();
                        List<PurchaseJson> purchaseJsons = gson.fromJson(response, new TypeToken<List<PurchaseJson>>() {
                        }.getType());
                        purchaseDao.updateServerSyncTime(purchaseJsons);

                    }
                }else if(httpStatusCode == 401){
                    isLoginFailed = true;
                    return;
                }

            }


        } catch (Exception e) {
            MobilePayAnalytics.getInstance().trackException(e,"Error in sendUnSyncDeclineData,Raw Data : purchaseEntityList["+purchaseEntityList+"],responseData["+responseData+"]");
            Log.e(LOG_TAG, "Error in sendUnSyncDeclineData", e);
        }
    }


    /**
     * Get TransactionalDetailsEntity
     * @param purchaseEntity
     * @return
     */
    private List<TransactionalDetailsEntity> getTransactionDetails(PurchaseEntity purchaseEntity){
        try {
            List<TransactionalDetailsEntity> transactionalDetailsEntities = purchaseDao.getTransactionalDetails(purchaseEntity);
            for(TransactionalDetailsEntity transactionalDetailsEntity : transactionalDetailsEntities){
                transactionalDetailsEntity.setPurchaseEntity(null);
                transactionalDetailsEntity.setTransactionId(0);
            }
            return transactionalDetailsEntities;
        }catch (Exception e){
            MobilePayAnalytics.getInstance().trackException(e,"Error in processTransactionDetails,Raw Data["+purchaseEntity+"]");
            Log.e(LOG_TAG, "Error in processTransactionDetails", e);
        }
        return new ArrayList<>();

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
