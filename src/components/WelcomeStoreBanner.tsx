import React from 'react';
import { Store, MapPin } from 'lucide-react';
import { StoreAccount } from '../types';

interface WelcomeStoreBannerProps {
  currentStore: StoreAccount;
}

export const WelcomeStoreBanner: React.FC<WelcomeStoreBannerProps> = ({
  currentStore,
}) => {
  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 shadow-2xs flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
      <div className="flex items-center gap-3.5">
        <div className="w-10 h-10 rounded-md bg-slate-900 text-white flex items-center justify-center font-bold text-lg shadow-2xs shrink-0">
          <Store className="w-5 h-5" />
        </div>
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-base sm:text-lg font-bold text-slate-900 tracking-tight">
              Welcome back, {currentStore.name}
            </h1>
            <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
              Verified Store
            </span>
          </div>
          <p className="text-xs text-slate-500 mt-0.5 flex flex-wrap items-center gap-2">
            <span className="flex items-center gap-1 font-medium text-slate-700">
              <MapPin className="w-3.5 h-3.5 text-slate-500" />
              {currentStore.address}
            </span>
            <span className="text-slate-300">•</span>
            <span className="text-slate-500">{currentStore.dockInfo}</span>
          </p>
        </div>
      </div>
    </div>
  );
};

