// Supabase Edge Function: wallet-payment
// Supports BOTH Automated Payment Gateways (SSLCommerz / bKash PGW) AND Manual bKash/Nagad TrxID submissions

import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
    );

    const payload = await req.json();
    const { action, user_id, amount_bdt, method, manual_trx_id, payment_phone, tx_id, admin_id } = payload;

    // Minimum wallet top-up validation (500 BDT, 1000 BDT or higher per Section 2.3)
    if (action === "initiate_topup") {
      if (!amount_bdt || amount_bdt < 500) {
        throw new Error("ন্যূনতম ৫০০ টাকা ক্রেডিট লোড করতে হবে।");
      }

      if (method === "manual_trx") {
        if (!manual_trx_id || String(manual_trx_id).trim().length < 6) {
          throw new Error("সঠিক bKash/Nagad Transaction ID প্রদান করুন।");
        }
        const { data, error } = await supabase
          .from("wallet_transactions")
          .insert({
            user_id,
            tx_type: "topup_manual_trx",
            amount_bdt,
            status: "pending",
            manual_trx_id: String(manual_trx_id).trim().toUpperCase(),
            payment_phone,
            notes: "Manual TrxID top-up awaiting admin approval",
          })
          .select()
          .single();

        if (error) throw error;
        return new Response(
          JSON.stringify({ mode: "manual_pending", transaction: data }),
          { headers: { ...corsHeaders, "Content-Type": "application/json" } },
        );
      }

      // SSLCommerz / bKash PGW Checkout Session Creation
      const txType = method === "bkash_pgw" ? "topup_bkash_pgw" : "topup_sslcommerz";
      const gatewayRef = `SPARC-${Date.now()}-${Math.floor(Math.random() * 9000 + 1000)}`;

      const { data: txRecord, error } = await supabase
        .from("wallet_transactions")
        .insert({
          user_id,
          tx_type: txType,
          amount_bdt,
          status: "pending",
          gateway_ref: gatewayRef,
          notes: `Initiated ${method} top-up`,
        })
        .select()
        .single();

      if (error) throw error;

      const storeId = Deno.env.get("SSLCOMMERZ_STORE_ID") ?? "sparc_sandbox";
      const checkoutUrl = method === "bkash_pgw"
        ? `https://checkout.sandbox.bka.sh/v1.2.0-beta/checkout?invoice=${gatewayRef}&amount=${amount_bdt}`
        : `https://sandbox.sslcommerz.com/EasyCheckOut/testcde?store_id=${storeId}&tran_id=${gatewayRef}&total_amount=${amount_bdt}`;

      return new Response(
        JSON.stringify({
          mode: "gateway_redirect",
          checkout_url: checkoutUrl,
          gateway_ref: gatewayRef,
          transaction: txRecord,
        }),
        { headers: { ...corsHeaders, "Content-Type": "application/json" } },
      );
    }

    // Webhook / Admin approval handler — atomically marks verified & triggers wallet balance sync
    if (action === "verify_topup") {
      const { data, error } = await supabase
        .from("wallet_transactions")
        .update({
          status: "verified",
          verified_by: admin_id ?? null,
          verified_at: new Date().toISOString(),
        })
        .eq("id", tx_id)
        .eq("status", "pending")
        .select()
        .single();

      if (error) throw error;
      return new Response(
        JSON.stringify({ verified: true, transaction: data }),
        { headers: { ...corsHeaders, "Content-Type": "application/json" } },
      );
    }

    throw new Error("Unsupported wallet action");
  } catch (err) {
    return new Response(
      JSON.stringify({ error: (err as Error).message }),
      { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } },
    );
  }
});
