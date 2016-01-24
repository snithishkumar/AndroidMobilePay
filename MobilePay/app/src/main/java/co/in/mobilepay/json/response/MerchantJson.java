package co.in.mobilepay.json.response;


public class MerchantJson {

	private String merchantName;
	private String address;
	private String area;
	private long mobileNumber;
	private long landNumber;

	public MerchantJson() {

	}


	public String getMerchantName() {
		return merchantName;
	}

	public void setMerchantName(String merchantName) {
		this.merchantName = merchantName;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getArea() {
		return area;
	}

	public void setArea(String area) {
		this.area = area;
	}

	public long getMobileNumber() {
		return mobileNumber;
	}

	public void setMobileNumber(long mobileNumber) {
		this.mobileNumber = mobileNumber;
	}

	public long getLandNumber() {
		return landNumber;
	}

	public void setLandNumber(long landNumber) {
		this.landNumber = landNumber;
	}

	@Override
	public String toString() {
		return "MerchantJson [merchantName=" + merchantName + ", address=" + address + ", area=" + area
				+ ", mobileNumber=" + mobileNumber + ", landNumber=" + landNumber + "]";
	}

}
