package co.in.mobilepay.dao.impl;

import android.content.Context;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.UpdateBuilder;
import com.j256.ormlite.stmt.query.In;

import java.sql.SQLException;
import java.util.List;

import co.in.mobilepay.dao.PurchaseDao;
import co.in.mobilepay.entity.DiscardEntity;
import co.in.mobilepay.entity.MerchantEntity;
import co.in.mobilepay.entity.PurchaseEntity;
import co.in.mobilepay.enumeration.OrderStatus;
import co.in.mobilepay.json.response.PurchaseJson;

/**
 * Created by Nithish on 24-01-2016.
 */
public class PurchaseDaoImpl extends BaseDaoImpl implements PurchaseDao {

    private Dao<PurchaseEntity,Integer> purchaseDao = null;
    private Dao<MerchantEntity,Integer> merchantDao = null;
    private Dao<DiscardEntity,Integer> discardDao = null;

    public PurchaseDaoImpl(Context context) throws SQLException {
        super(context);
        initDao();
    }

    /**
     * Initialize all the DAO's
     * @throws SQLException
     */
    @Override
    protected void initDao() throws SQLException {
        purchaseDao = databaseHelper.getDao(PurchaseEntity.class);
        merchantDao = databaseHelper.getDao(MerchantEntity.class);
        discardDao = databaseHelper.getDao(DiscardEntity.class);
    }

    /**
     * Insert a New Record
     * @param purchaseEntity
     * @throws SQLException
     */
    @Override
    public void createPurchase(PurchaseEntity purchaseEntity) throws SQLException {
        purchaseDao.create(purchaseEntity);
    }

    /**
     * Update an Existing record
     * @param purchaseEntity
     * @throws SQLException
     */
    @Override
    public void updatePurchase(PurchaseEntity purchaseEntity) throws SQLException {
        purchaseDao.update(purchaseEntity);
    }

    /**
     * Get List of un payed PurchaseEntity
     * @return List of PurchaseEntity or empty list
     * @throws SQLException
     */
    @Override
    public List<PurchaseEntity> getPurchaseList() throws SQLException {
        return purchaseDao.queryBuilder().where().eq(PurchaseEntity.IS_PAYED,false).and().eq(PurchaseEntity.IS_DISCARD,false).query();
    }

    /**
     * Returns last Purchased uuid
     * @return purchase uuid or null
     * @throws SQLException
     */
    @Override
    public String getLastPurchaseId() throws SQLException {
        PurchaseEntity purchaseEntity = purchaseDao.queryBuilder().selectColumns(PurchaseEntity.PURCHASE_GUID).orderBy(PurchaseEntity.PURCHASE_ID,false).queryForFirst();
        return  purchaseEntity != null ? purchaseEntity.getPurchaseGuid() : null;
    }

    @Override
    public PurchaseEntity getPurchaseEntity(int purchaseId) throws SQLException {
        return purchaseDao.queryForId(purchaseId);
    }



    @Override
    public PurchaseEntity getPurchaseEntity(String purchaseId) throws SQLException {
        return purchaseDao.queryBuilder().where().eq(PurchaseEntity.PURCHASE_GUID,purchaseId).queryForFirst();
    }

    /**
     * Get List of  payed PurchaseEntity
     * @return List of PurchaseEntity or empty list
     * @throws SQLException
     */
    @Override
    public List<PurchaseEntity> getPurchaseHistoryList() throws SQLException {
        QueryBuilder<PurchaseEntity,Integer> currentPurchaseQueryBuilder =  purchaseDao.queryBuilder();
        currentPurchaseQueryBuilder.where().eq(PurchaseEntity.ORDER_STATUS, OrderStatus.CANCELED.toString()).or().eq(PurchaseEntity.ORDER_STATUS, OrderStatus.DELIVERED.toString());
       return currentPurchaseQueryBuilder.orderBy(PurchaseEntity.UPDATED_DATE_TIME, false).query();
    }

    /**
     * Returns Current Most recent Purchase (Not Payed and Not Discard) server time otherwise it will returns -1
     * @return
     * @throws SQLException
     */
    @Override
    public long getMostRecentPurchaseServerTime()throws SQLException{
       QueryBuilder<PurchaseEntity,Integer> currentPurchaseQueryBuilder =  purchaseDao.queryBuilder();
       currentPurchaseQueryBuilder.where().eq(PurchaseEntity.IS_PAYED, false).and().eq(PurchaseEntity.IS_DISCARD, false);
        PurchaseEntity purchaseEntity =  currentPurchaseQueryBuilder.orderBy(PurchaseEntity.SERVER_DATE_TIME, false).queryForFirst();
        return  purchaseEntity != null ? purchaseEntity.getServerDateTime() : -1;
    }


    /**
     * Returns  Most recent Purchase History (DELIVERED or  CANCELED) server time otherwise it will returns -1
     * @return
     * @throws SQLException
     */
    @Override
    public long getRecentPurchaseHisServerTime()throws SQLException{
        QueryBuilder<PurchaseEntity,Integer> currentPurchaseQueryBuilder =  purchaseDao.queryBuilder();
        currentPurchaseQueryBuilder.where().eq(PurchaseEntity.ORDER_STATUS, OrderStatus.CANCELED.toString()).or().eq(PurchaseEntity.ORDER_STATUS, OrderStatus.DELIVERED.toString());

        PurchaseEntity purchaseEntity =  currentPurchaseQueryBuilder.orderBy(PurchaseEntity.SERVER_DATE_TIME, false).queryForFirst();
        return  purchaseEntity != null ? purchaseEntity.getServerDateTime() : -1;
    }


    /**
     * Returns Luggage List (Payed, not canceled and not delivered) Server Time
     * @return
     * @throws SQLException
     */
    @Override
    public List<PurchaseEntity> getOrderStatusList()throws SQLException{
        QueryBuilder<PurchaseEntity,Integer> luggageQueryBuilder =  purchaseDao.queryBuilder();
        luggageQueryBuilder.where().eq(PurchaseEntity.IS_PAYED, true).and()
                .ne(PurchaseEntity.ORDER_STATUS, OrderStatus.CANCELED.toString()).and()
                .ne(PurchaseEntity.ORDER_STATUS, OrderStatus.DELIVERED.toString());
        return  luggageQueryBuilder.orderBy(PurchaseEntity.UPDATED_DATE_TIME, false).query();
    }



    /**
     * Returns first Luggage List (Payed, not canceled and not delivered) Server Time
     * @return
     * @throws SQLException
     */
    @Override
    public long getLeastLuggageServerTime()throws SQLException{
        QueryBuilder<PurchaseEntity,Integer> luggageQueryBuilder =  purchaseDao.queryBuilder();
        luggageQueryBuilder.where().eq(PurchaseEntity.IS_PAYED, true).and()
                .ne(PurchaseEntity.ORDER_STATUS, OrderStatus.CANCELED.toString()).and()
                .ne(PurchaseEntity.ORDER_STATUS, OrderStatus.DELIVERED.toString());
        PurchaseEntity purchaseEntity =  luggageQueryBuilder.orderBy(PurchaseEntity.PURCHASE_DATE_TIME, true).queryForFirst();
        return  purchaseEntity != null ? purchaseEntity.getPurchaseDateTime() : -1;
    }

    /**
     * Returns Most recent Luggage List (Payed, not canceled and not delivered) Server Time
     * @return
     * @throws SQLException
     */
    @Override
    public long getMostRecentLuggageServerTime()throws SQLException{
        QueryBuilder<PurchaseEntity,Integer> luggageQueryBuilder =  purchaseDao.queryBuilder();
        luggageQueryBuilder.where().eq(PurchaseEntity.IS_PAYED, true).and()
                .ne(PurchaseEntity.ORDER_STATUS, OrderStatus.CANCELED.toString()).and()
                .ne(PurchaseEntity.ORDER_STATUS, OrderStatus.DELIVERED.toString());
        PurchaseEntity purchaseEntity =  luggageQueryBuilder.orderBy(PurchaseEntity.PURCHASE_DATE_TIME, true).queryForFirst();
        return  purchaseEntity != null ? purchaseEntity.getPurchaseDateTime() : -1;
    }

    /**
     * Returns merchant entity or null
     * @param merchantGuid
     * @return
     */
    @Override
    public MerchantEntity getMerchantEntity(String merchantGuid)throws SQLException{
        return  merchantDao.queryBuilder().where().eq(MerchantEntity.MERCHANT_GUID,merchantGuid).queryForFirst();
    }

    @Override
    public void createMerchantEntity(MerchantEntity merchantEntity)throws SQLException{
        merchantDao.create(merchantEntity);
    }

    @Override
    public void updateMerchantEntity(MerchantEntity merchantEntity)throws SQLException{
        merchantDao.update(merchantEntity);
    }


    @Override
    public void createDiscardEntity(DiscardEntity discardEntity)throws SQLException{
        discardDao.create(discardEntity);
    }


    @Override
    public List<PurchaseEntity> getUnSyncedDiscardEntity()throws SQLException{
       return purchaseDao.queryBuilder().orderBy(PurchaseEntity.UPDATED_DATE_TIME,true).where().eq(PurchaseEntity.IS_DISCARD,true).and().eq(PurchaseEntity.IS_SYNC,false).query();
    }


    @Override
    public List<PurchaseEntity> getUnSyncedPayedEntity()throws SQLException{
        return purchaseDao.queryBuilder().orderBy(PurchaseEntity.UPDATED_DATE_TIME,true).where().eq(PurchaseEntity.IS_PAYED,true).and().eq(PurchaseEntity.IS_SYNC,false).query();
    }


    @Override
    public void updateServerSyncTime(List<PurchaseJson> purchaseJsonList)throws SQLException{
        for(PurchaseJson purchaseJson : purchaseJsonList){
            UpdateBuilder<PurchaseEntity,Integer> updateBuilder =  purchaseDao.updateBuilder();
            updateBuilder.updateColumnValue(PurchaseEntity.IS_SYNC,true);
            updateBuilder.updateColumnValue(PurchaseEntity.SERVER_DATE_TIME,purchaseJson.getServerDateTime());
            updateBuilder.where().eq(PurchaseEntity.PURCHASE_GUID,purchaseJson.getPurchaseId() );
            updateBuilder.update();
        }

    }


    @Override
    public DiscardEntity getDiscardEntity(PurchaseEntity purchaseEntity)throws SQLException{
        return discardDao.queryBuilder().where().eq(DiscardEntity.PURCHASE_ID,purchaseEntity).queryForFirst();
    }
}
