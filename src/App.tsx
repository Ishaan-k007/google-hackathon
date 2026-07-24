import React, { useState } from 'react';
import { Header } from './components/Header';
import { WelcomeStoreBanner } from './components/WelcomeStoreBanner';
import { SupermarketForm } from './components/SupermarketForm';
import { YourAvailableItems } from './components/YourAvailableItems';
import { SwitchAccountView } from './components/SwitchAccountView';
import { FoodBankView } from './components/FoodBankView';
import { FoodOffer, FoodBankPartner, StoreAccount, SupermarketTab, AccountRoleMode } from './types';
import { INITIAL_FOOD_OFFERS, INITIAL_FOOD_BANKS, INITIAL_STORE_ACCOUNTS } from './data/mockData';
import { HeartHandshake, Building2, Store } from 'lucide-react';

export default function App() {
  const [activeTab, setActiveTab] = useState<SupermarketTab>('FLAG_SURPLUS');
  const [activeRole, setActiveRole] = useState<AccountRoleMode>('SUPERMARKET');
  const [stores, setStores] = useState<StoreAccount[]>(INITIAL_STORE_ACCOUNTS);
  const [currentStore, setCurrentStore] = useState<StoreAccount>(INITIAL_STORE_ACCOUNTS[0]); // Tesco - St Pancras
  const [foodBanks, setFoodBanks] = useState<FoodBankPartner[]>(INITIAL_FOOD_BANKS);
  const [currentFoodBank, setCurrentFoodBank] = useState<FoodBankPartner>(INITIAL_FOOD_BANKS[0]);
  const [offers, setOffers] = useState<FoodOffer[]>(INITIAL_FOOD_OFFERS);

  const handleAddOffer = (newOfferData: Omit<FoodOffer, 'id' | 'createdAt' | 'status'>) => {
    const newOffer: FoodOffer = {
      ...newOfferData,
      id: `offer-${Date.now()}`,
      createdAt: new Date().toISOString(),
      status: 'AVAILABLE',
    };
    setOffers((prev) => [newOffer, ...prev]);
  };

  const handleAddStore = (newStore: StoreAccount) => {
    setStores((prev) => [newStore, ...prev]);
  };

  const handleAddFoodBank = (newFb: FoodBankPartner) => {
    setFoodBanks((prev) => [newFb, ...prev]);
  };

  const handleClaimOffer = (offerId: string, foodBankName: string) => {
    setOffers((prev) =>
      prev.map((o) => {
        if (o.id === offerId) {
          return {
            ...o,
            status: 'CLAIMED',
            claimedBy: foodBankName,
            claimedAt: new Date().toISOString(),
          };
        }
        return o;
      })
    );
  };

  const handleCollectOffer = (offerId: string, inputCode: string): boolean => {
    const target = offers.find((o) => o.id === offerId);
    if (!target) return false;

    if (target.pickupCode.trim().toUpperCase() === inputCode.trim().toUpperCase()) {
      setOffers((prev) =>
        prev.map((o) => {
          if (o.id === offerId) {
            return {
              ...o,
              status: 'COLLECTED',
              collectedAt: new Date().toISOString(),
            };
          }
          return o;
        })
      );
      return true;
    }
    return false;
  };

  const availableItemsCount = offers.filter(
    (o) => o.storeName === currentStore.name && o.status === 'AVAILABLE'
  ).length;

  return (
    <div className="min-h-screen bg-slate-50 text-slate-800 font-sans antialiased flex flex-col justify-between">
      <div>
        {/* Top Header */}
        <Header
          activeTab={activeTab}
          setActiveTab={(tab) => {
            setActiveTab(tab);
            if (activeRole === 'FOOD_BANK' && tab !== 'SWITCH_ACCOUNT') {
              setActiveRole('SUPERMARKET');
            }
          }}
          activeRole={activeRole}
          currentStore={currentStore}
          currentFoodBank={currentFoodBank}
          availableItemsCount={availableItemsCount}
        />

        {/* Main Content Area */}
        <main className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
          
          {/* Case A: Supermarket Role View */}
          {activeRole === 'SUPERMARKET' && (
            <div>
              {/* Store Welcome Banner */}
              <WelcomeStoreBanner
                currentStore={currentStore}
              />

              {/* Tab 1: Flag Surplus */}
              {activeTab === 'FLAG_SURPLUS' && (
                <SupermarketForm
                  currentStore={currentStore}
                  onAddOffer={handleAddOffer}
                  onNavigateToAvailableItems={() => setActiveTab('YOUR_ITEMS')}
                />
              )}

              {/* Tab 2: Your Available Items */}
              {activeTab === 'YOUR_ITEMS' && (
                <YourAvailableItems
                  currentStore={currentStore}
                  offers={offers}
                  onNavigateToFlag={() => setActiveTab('FLAG_SURPLUS')}
                />
              )}

              {/* Tab 3: Switch Account */}
              {activeTab === 'SWITCH_ACCOUNT' && (
                <SwitchAccountView
                  stores={stores}
                  currentStore={currentStore}
                  onSelectStore={setCurrentStore}
                  onAddStore={handleAddStore}
                  onAddFoodBank={handleAddFoodBank}
                  activeRole={activeRole}
                  onSelectRole={setActiveRole}
                />
              )}
            </div>
          )}

          {/* Case B: Food Bank Role View (when user switched to charity account) */}
          {activeRole === 'FOOD_BANK' && (
            <div className="space-y-6">
              
              {/* Charity View Banner */}
              <div className="bg-amber-50 border border-amber-200 rounded-2xl p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-amber-900 text-xs font-semibold">
                <div className="flex items-center gap-2">
                  <Building2 className="w-5 h-5 text-amber-600 shrink-0" />
                  <span>
                    Logged in as Charity Provider: <strong>{currentFoodBank.name}</strong>. Claim surplus items & verify pickup codes.
                  </span>
                </div>

                <div className="flex items-center gap-2">
                  <button
                    onClick={() => {
                      setActiveRole('SUPERMARKET');
                      setActiveTab('FLAG_SURPLUS');
                    }}
                    className="px-3 py-1.5 bg-slate-900 hover:bg-slate-800 text-white font-bold rounded-xl text-xs flex items-center gap-1 shadow-2xs"
                  >
                    <Store className="w-3.5 h-3.5 text-sky-400" />
                    <span>Return to Supermarket Portal ({currentStore.name})</span>
                  </button>
                </div>
              </div>

              {activeTab === 'SWITCH_ACCOUNT' ? (
                <SwitchAccountView
                  stores={stores}
                  currentStore={currentStore}
                  onSelectStore={setCurrentStore}
                  onAddStore={handleAddStore}
                  onAddFoodBank={handleAddFoodBank}
                  activeRole={activeRole}
                  onSelectRole={setActiveRole}
                />
              ) : (
                <FoodBankView
                  offers={offers}
                  foodBanks={foodBanks}
                  onClaimOffer={handleClaimOffer}
                  onCollectOffer={handleCollectOffer}
                />
              )}
            </div>
          )}

        </main>
      </div>

      {/* Footer */}
      <footer className="border-t border-slate-200 bg-white py-4 text-center text-xs text-slate-500">
        <div className="max-w-5xl mx-auto px-4 flex flex-col sm:flex-row items-center justify-between gap-2">
          <span className="flex items-center gap-1 font-semibold text-slate-700">
            <HeartHandshake className="w-4 h-4 text-sky-600" /> Zero FoodWastage Network
          </span>
          <span>Connecting supermarket surplus directly with verified food bank charities</span>
        </div>
      </footer>
    </div>
  );
}
