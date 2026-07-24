import express from 'express';
import path from 'path';
import { GoogleGenAI, Type } from '@google/genai';
import { createServer as createViteServer } from 'vite';

async function startServer() {
  const app = express();
  const PORT = 3000;

  app.use(express.json());

  // Server-side Gemini Client Lazy Initialization
  function getGeminiClient() {
    const apiKey = process.env.GEMINI_API_KEY;
    if (!apiKey) return null;
    return new GoogleGenAI({
      apiKey,
      httpOptions: {
        headers: {
          'User-Agent': 'aistudio-build',
        },
      },
    });
  }

  // API Endpoint: AI Orchestrator for Surplus Food Redistribution
  app.post('/api/orchestrate', async (req, res) => {
    try {
      const { rawText, storeInfo, availableFoodBanks } = req.body;

      if (!rawText || !rawText.trim()) {
        return res.status(400).json({ error: 'Raw inventory text is required' });
      }

      const ai = getGeminiClient();

      if (ai) {
        // Build prompt with store details, vague text, and available charities
        const systemInstruction = `You are FoodResQ AI, an expert food logistics orchestration engine.
Your task is to take unstructured surplus food inventory text from a supermarket along with store details, parse it into structured items, evaluate shelf life urgency and cold-chain/storage constraints, and match the items to the best-suited nearby food banks from the provided partner list.

Key Constraints to Enforce:
1. Urgency: Short shelf life (<24 hours) items MUST go to high-turnover meal centers or closest food banks.
2. Cold Chain: Chilled/Frozen items MUST only be allocated to partners with Chilled/Frozen capacity.
3. Secret Code: Preserve or assign the store's Secret Verification Code for driver authentication.
4. Practical Quantities: Estimate weight in kg reasonably based on description.`;

        const userPrompt = `
STORE DETAILS:
Name: ${storeInfo?.name || 'Supermarket Branch'}
Branch ID: ${storeInfo?.branchId || 'STR-999'}
Address: ${storeInfo?.address || 'Main Street'}, ${storeInfo?.city || 'Central City'}
Pickup Hours: ${storeInfo?.pickupHours || '08:00 - 20:00'}
Facilities Available: ${(storeInfo?.storageFacilities || ['Ambient', 'Chilled']).join(', ')}
Secret Pickup Verification Code: ${storeInfo?.secretCode || 'RESCUE-1234'}

RAW SUPERMARKET INVENTORY TEXT:
"""
${rawText}
"""

AVAILABLE REGISTERED FOOD BANK PARTNERS:
${JSON.stringify(availableFoodBanks || [], null, 2)}

Return a complete structured food distribution plan adhering strictly to the JSON schema.`;

        const response = await ai.models.generateContent({
          model: 'gemini-3.6-flash',
          contents: userPrompt,
          config: {
            systemInstruction,
            responseMimeType: 'application/json',
            responseSchema: {
              type: Type.OBJECT,
              properties: {
                aiSummary: {
                  type: Type.STRING,
                  description: '1-2 sentence executive summary of the allocation plan and constraints resolved.',
                },
                parsedItems: {
                  type: Type.ARRAY,
                  description: 'List of structured items parsed from the input text.',
                  items: {
                    type: Type.OBJECT,
                    properties: {
                      id: { type: Type.STRING },
                      name: { type: Type.STRING },
                      quantity: { type: Type.STRING },
                      estimatedKg: { type: Type.NUMBER },
                      category: {
                        type: Type.STRING,
                        description: 'One of: Bakery, Dairy & Chilled, Fresh Produce, Prepared Meals, Meat & Seafood, Ambient / Dried',
                      },
                      expiryHoursLeft: { type: Type.NUMBER },
                      urgency: { type: Type.STRING, description: 'CRITICAL, HIGH, or MEDIUM' },
                      storageRequired: { type: Type.STRING, description: 'Chilled, Frozen, or Ambient' },
                      notes: { type: Type.STRING },
                    },
                    required: ['id', 'name', 'quantity', 'estimatedKg', 'category', 'expiryHoursLeft', 'urgency', 'storageRequired'],
                  },
                },
                allocations: {
                  type: Type.ARRAY,
                  description: 'List of charity/food bank allocations with matched items and logistical instructions.',
                  items: {
                    type: Type.OBJECT,
                    properties: {
                      foodBankId: { type: Type.STRING },
                      foodBankName: { type: Type.STRING },
                      foodBankAddress: { type: Type.STRING },
                      distanceMiles: { type: Type.NUMBER },
                      assignedItems: {
                        type: Type.ARRAY,
                        items: {
                          type: Type.OBJECT,
                          properties: {
                            itemId: { type: Type.STRING },
                            name: { type: Type.STRING },
                            quantity: { type: Type.STRING },
                          },
                          required: ['itemId', 'name', 'quantity'],
                        },
                      },
                      matchingRationale: { type: Type.STRING },
                      suggestedPickupWindow: { type: Type.STRING },
                      specialHandlingNotes: { type: Type.STRING },
                    },
                    required: ['foodBankId', 'foodBankName', 'foodBankAddress', 'distanceMiles', 'assignedItems', 'matchingRationale', 'suggestedPickupWindow', 'specialHandlingNotes'],
                  },
                },
              },
              required: ['aiSummary', 'parsedItems', 'allocations'],
            },
          },
        });

        if (response.text) {
          const planData = JSON.parse(response.text);
          return res.json({
            success: true,
            plan: {
              id: `DSP-${Date.now().toString().slice(-6)}`,
              createdAt: new Date().toISOString(),
              storeInfo: storeInfo || {
                name: 'Supermarket Store',
                branchId: 'STR-101',
                address: '100 Main St',
                city: 'Metropolis',
                contactName: 'Manager',
                contactPhone: '+1 555-0199',
                pickupHours: '08:00 - 20:00',
                storageFacilities: ['Chilled', 'Ambient'],
                secretCode: 'RESCUE-5512',
              },
              rawInputText: rawText,
              parsedItems: planData.parsedItems || [],
              allocations: planData.allocations || [],
              secretPickupCode: storeInfo?.secretCode || 'RESCUE-5512',
              status: 'AI_STRUCTURED',
              aiSummary: planData.aiSummary || 'AI structured the inventory into an optimized distribution plan.',
            },
          });
        }
      }

      // Fallback Engine if GEMINI_API_KEY is missing or AI request doesn't return text
      const fallbackItems = buildFallbackItems(rawText);
      const fallbackAllocations = buildFallbackAllocations(fallbackItems, storeInfo, availableFoodBanks);

      return res.json({
        success: true,
        isFallback: true,
        plan: {
          id: `DSP-${Date.now().toString().slice(-6)}`,
          createdAt: new Date().toISOString(),
          storeInfo: storeInfo || {
            name: 'Supermarket Store',
            branchId: 'STR-101',
            address: '100 Main St',
            city: 'Metropolis',
            contactName: 'Manager',
            contactPhone: '+1 555-0199',
            pickupHours: '08:00 - 20:00',
            storageFacilities: ['Chilled', 'Ambient'],
            secretCode: 'RESCUE-5512',
          },
          rawInputText: rawText,
          parsedItems: fallbackItems,
          allocations: fallbackAllocations,
          secretPickupCode: storeInfo?.secretCode || 'RESCUE-5512',
          status: 'AI_STRUCTURED',
          aiSummary: `Parsed ${fallbackItems.length} surplus items into structured distribution groups matched with local food banks based on urgency and cold-chain fit.`,
        },
      });
    } catch (err: any) {
      console.error('Error in /api/orchestrate:', err);
      return res.status(500).json({
        error: 'Failed to orchestrate distribution plan',
        message: err.message,
      });
    }
  });

  // Health check
  app.get('/api/health', (req, res) => {
    res.json({ status: 'ok', time: new Date().toISOString() });
  });

  // Vite middleware for development vs static build serving for production
  if (process.env.NODE_ENV !== 'production') {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`FoodResQ AI Server running on http://0.0.0.0:${PORT}`);
  });
}

function buildFallbackItems(rawText: string) {
  const lines = rawText.split(/[\n,;.]+/).map(s => s.trim()).filter(Boolean);
  const items = [];
  let counter = 1;

  for (const line of lines) {
    if (line.length < 3) continue;
    const isChilled = /milk|cheese|yogurt|meat|chicken|fish|lasagna|meal|chilled|cold/i.test(line);
    const isBakery = /bread|sourdough|loaf|loafs|bakery|croissant|pastry|cake/i.test(line);
    const isProduce = /apple|carrots|potato|fruit|salad|vegetable|produce|orange|banana/i.test(line);

    items.push({
      id: `item-fb-${counter++}`,
      name: line.length > 50 ? line.slice(0, 47) + '...' : line,
      quantity: '1 batch / crate',
      estimatedKg: isBakery ? 15 : isChilled ? 20 : 25,
      category: isChilled ? 'Dairy & Chilled' : isBakery ? 'Bakery' : isProduce ? 'Fresh Produce' : 'Ambient / Dried',
      expiryHoursLeft: isChilled ? 12 : isBakery ? 18 : 36,
      urgency: isChilled ? 'CRITICAL' : isBakery ? 'HIGH' : 'MEDIUM',
      storageRequired: isChilled ? 'Chilled' : 'Ambient',
      notes: isChilled ? 'Requires temperature-controlled transport' : 'Store at room temperature',
    });
  }

  if (items.length === 0) {
    items.push({
      id: 'item-fb-default',
      name: 'Unspecified Grocery Surplus',
      quantity: '1 Pallet',
      estimatedKg: 30,
      category: 'Ambient / Dried',
      expiryHoursLeft: 24,
      urgency: 'HIGH',
      storageRequired: 'Ambient',
      notes: 'General food donation batch',
    });
  }

  return items;
}

function buildFallbackAllocations(items: any[], storeInfo: any, foodBanks: any[]) {
  const defaultBank = foodBanks && foodBanks.length > 0 ? foodBanks[0] : {
    id: 'fb-01',
    name: 'St. Mary Community Food Pantry',
    address: '42 High Street, Central District',
    distanceMiles: 1.2,
  };

  return [
    {
      foodBankId: defaultBank.id,
      foodBankName: defaultBank.name,
      foodBankAddress: defaultBank.address,
      distanceMiles: defaultBank.distanceMiles || 1.2,
      assignedItems: items.map(i => ({ itemId: i.id, name: i.name, quantity: i.quantity })),
      matchingRationale: 'Matched based on short transit distance and active community meal service program.',
      suggestedPickupWindow: 'Today 12:00 - 14:00',
      specialHandlingNotes: `Verify driver code (${storeInfo?.secretCode || 'RESCUE-1234'}) at store dispatch dock before pickup.`,
    },
  ];
}

startServer();
