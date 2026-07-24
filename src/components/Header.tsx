import React from 'react';
import { Send, Package, UserCheck, Store, Building2 } from 'lucide-react';
import { SupermarketTab, AccountRoleMode, StoreAccount, FoodBankPartner } from '../types';

interface HeaderProps {
  activeTab: SupermarketTab;
  setActiveTab: (tab: SupermarketTab) => void;
  activeRole: AccountRoleMode;
  currentStore: StoreAccount;
  currentFoodBank: FoodBankPartner;
  availableItemsCount: number;
}

export const Header: React.FC<HeaderProps> = ({
  activeTab,
  setActiveTab,
  activeRole,
  currentStore,
  currentFoodBank,
  availableItemsCount,
}) => {
  return (
    <header className="bg-white border-b border-slate-200 text-slate-800 sticky top-0 z-40">
      <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex flex-col md:flex-row items-center justify-between py-3.5 gap-3">
          
          {/* Brand Logo & Title */}
          <div className="flex items-center gap-3">
            <div>
              <div className="flex items-center gap-2">
                <span className="font-extrabold text-base sm:text-lg tracking-tight text-slate-900">
                  Zero <span className="text-emerald-700">FoodWastage</span>
                </span>
                <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-slate-100 text-slate-700 border border-slate-200">
                  Supermarket Portal
                </span>
              </div>
              <p className="text-xs text-slate-500">
                Direct Supermarket Surplus Dispatch & Charity Rescue
              </p>
            </div>
          </div>

          {/* Navigation Tabs */}
          <div className="flex flex-wrap items-center gap-1 bg-slate-100 p-1 rounded-md border border-slate-200">
            <button
              onClick={() => setActiveTab('FLAG_SURPLUS')}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold transition-all ${
                activeTab === 'FLAG_SURPLUS' && activeRole === 'SUPERMARKET'
                  ? 'bg-white text-slate-900 shadow-2xs border border-slate-200 font-bold'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <Send className="w-3.5 h-3.5 text-slate-600" />
              <span>Flag Surplus</span>
            </button>

            <button
              onClick={() => setActiveTab('YOUR_ITEMS')}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold transition-all ${
                activeTab === 'YOUR_ITEMS' && activeRole === 'SUPERMARKET'
                  ? 'bg-white text-slate-900 shadow-2xs border border-slate-200 font-bold'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <Package className="w-3.5 h-3.5 text-slate-600" />
              <span>Your Available Items</span>
              {availableItemsCount > 0 && (
                <span className="px-1.5 py-0.2 rounded text-[10px] font-bold bg-slate-800 text-white">
                  {availableItemsCount}
                </span>
              )}
            </button>

            <button
              onClick={() => setActiveTab('SWITCH_ACCOUNT')}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold transition-all ${
                activeTab === 'SWITCH_ACCOUNT'
                  ? 'bg-white text-slate-900 shadow-2xs border border-slate-200 font-bold'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <UserCheck className="w-3.5 h-3.5 text-slate-600" />
              <span>Switch Account</span>
            </button>
          </div>

          {/* Active Logged In Account Pill */}
          <div className="hidden lg:flex items-center gap-2 px-3 py-1.5 rounded-md bg-slate-50 border border-slate-200 text-xs font-medium text-slate-700">
            {activeRole === 'SUPERMARKET' ? (
              <>
                <Store className="w-3.5 h-3.5 text-slate-600" />
                <span className="truncate max-w-[160px] font-semibold">{currentStore.name}</span>
              </>
            ) : (
              <>
                <Building2 className="w-3.5 h-3.5 text-amber-600" />
                <span className="truncate max-w-[160px] font-semibold">{currentFoodBank.name}</span>
              </>
            )}
          </div>

        </div>
      </div>
    </header>
  );
};

