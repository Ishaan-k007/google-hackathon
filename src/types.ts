export type FoodOfferStatus = 'AVAILABLE' | 'CLAIMED' | 'COLLECTED';

export interface FoodOffer {
  id: string;
  storeName: string;
  pickupLocation: string;
  pickupCode: string;
  description: string;
  createdAt: string;
  status: FoodOfferStatus;
  claimedBy?: string;
  claimedAt?: string;
  collectedAt?: string;
  notes?: string;
}

export interface StoreAccount {
  id: string;
  name: string;
  address: string;
  dockInfo: string;
  defaultCodePrefix: string;
}

export interface FoodBankPartner {
  id: string;
  name: string;
  location: string;
}

export type SupermarketTab = 'FLAG_SURPLUS' | 'YOUR_ITEMS' | 'SWITCH_ACCOUNT';
export type AccountRoleMode = 'SUPERMARKET' | 'FOOD_BANK';

