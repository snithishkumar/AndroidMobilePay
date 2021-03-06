package co.in.mobilepay.view.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.List;

import co.in.mobilepay.R;
import co.in.mobilepay.application.MobilePayAnalytics;
import co.in.mobilepay.entity.MerchantEntity;
import co.in.mobilepay.entity.PurchaseEntity;
import co.in.mobilepay.json.response.CalculatedAmounts;
import co.in.mobilepay.service.PurchaseService;
import co.in.mobilepay.util.MobilePayUtil;
import co.in.mobilepay.view.activities.PurchaseDetailsActivity;
import co.in.mobilepay.view.adapters.MobilePayDividerItemDetoration;
import co.in.mobilepay.view.adapters.ProductDetailsHistoryAdapter;
import co.in.mobilepay.view.model.AmountDetailsJson;
import co.in.mobilepay.view.model.ProductDetailsModel;

/**
 * A fragment representing a list of Items.
 * <p/>
 */
public class ProductHistoryDetailsFragment extends Fragment{

    private PurchaseDetailsActivity purchaseDetailsActivity;
    private PurchaseService purchaseService;
    private int purchaseId = 0;

    private TextView shopName = null;
    private TextView shopArea = null;
    private TextView shopOrderId = null;
    private TextView shoppingDateTime = null;


    private Gson gson = null;


    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ProductHistoryDetailsFragment() {

    }



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Bundle purchaseIdArgs = getArguments();
        if(purchaseIdArgs != null){
            purchaseId =  purchaseIdArgs.getInt("purchaseId");
        }
        View view = inflater.inflate(R.layout.fragment_product_history_list, container, false);
        initView(view);
        populatePurchaseData(view);
        return view;
    }

    /**
     * Initialize
     * @param view
     */
    private void initView(View view){
        if(gson == null){
            gson = new Gson();
        }
        shopName = (TextView)view.findViewById(R.id.purchase_history_shop_name);
        shopArea = (TextView)view.findViewById(R.id.purchase_history_shop_area);
        shoppingDateTime  = (TextView)view.findViewById(R.id.purchase_history_shop_date_time);
        shopOrderId = (TextView)view.findViewById(R.id.purchase_history_shop_order_id);

    }


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        purchaseDetailsActivity = (PurchaseDetailsActivity)context;
        purchaseService = purchaseDetailsActivity.getPurchaseService();
    }

    /**
     * Populate value
     */
    private void populatePurchaseData(View view){
        PurchaseEntity purchaseEntity = purchaseService.getPurchaseDetails(purchaseId);
        MerchantEntity merchantEntity = purchaseEntity.getMerchantEntity();
        shopName.setText(merchantEntity.getMerchantName());
        shopArea.setText("("+merchantEntity.getArea()+")");
        shopOrderId.append( purchaseEntity.getBillNumber());
        String purchaseDateTime =  MobilePayUtil.formatDate(purchaseEntity.getPurchaseDateTime());
        shoppingDateTime.setText(purchaseDateTime);

        String productDetails = purchaseEntity.getProductDetails();

        List<ProductDetailsModel> productDetailsModelList = gson.fromJson(productDetails, new TypeToken<List<ProductDetailsModel>>() {
        }.getType());


        // Set the adapter
        RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.purchase_history_product_items_view);

        LinearLayoutManager linearLayoutManager =  new LinearLayoutManager(purchaseDetailsActivity);
        recyclerView.setLayoutManager(linearLayoutManager);
        recyclerView.setHasFixedSize(true);

       String calculated =  purchaseEntity.getCalculatedAmountDetails();
        CalculatedAmounts calculatedAmounts = gson.fromJson(calculated, CalculatedAmounts.class);

        String amountDetails = purchaseEntity.getAmountDetails();
        AmountDetailsJson amountDetailsJson = gson.fromJson(amountDetails, AmountDetailsJson.class);

        ProductDetailsHistoryAdapter productDetailsHistoryAdapter = new ProductDetailsHistoryAdapter(purchaseDetailsActivity, productDetailsModelList, amountDetailsJson, purchaseEntity, calculatedAmounts);
        recyclerView.setAdapter(productDetailsHistoryAdapter);
        recyclerView.addItemDecoration(new MobilePayDividerItemDetoration(
                getContext()
        ));
    }

    @Override
    public void onResume(){
        MobilePayAnalytics.getInstance().trackScreenView("Product History Details-F Screen");
        super.onResume();
    }


    @Override
    public void onDetach() {
        super.onDetach();
    }


}
