const CATEGORIES=[
{slug:'fantasy',title:'قصص فانتازيا',emoji:'✨',sort_order:1},
{slug:'forced-marriage',title:'الزواج الإجباري',emoji:'💍',sort_order:2},
{slug:'history',title:'قصص تاريخية',emoji:'🏰',sort_order:3},
{slug:'children',title:'قصص أطفال',emoji:'🧸',sort_order:4},
{slug:'romance',title:'قصص رومانسية',emoji:'🌹',sort_order:5},
{slug:'horror',title:'قصص رعب',emoji:'🌙',sort_order:6},
{slug:'alf-layla',title:'ألف ليلة وليلة',emoji:'🪔',sort_order:7}
];

const json=(data,status=200,headers={})=>new Response(JSON.stringify(data),{
 status,
 headers:{
  'Content-Type':'application/json; charset=utf-8',
  'Cache-Control':'no-store',
  ...headers
 }
});

const corsHeaders=(origin)=>({
 'Access-Control-Allow-Origin':origin||'*',
 'Access-Control-Allow-Methods':'GET,POST,PUT,DELETE,OPTIONS',
 'Access-Control-Allow-Headers':'Content-Type,Authorization,X-Visitor-ID',
 'Vary':'Origin'
});

function validOrigin(origin){
 // Native Android requests may omit Origin. Android WebView commonly uses
 // https://localhost; some local WebViews send the opaque origin 'null'.
 return !origin||
 origin==='null'||
 /^https:\/\/[a-z0-9-]+\.netlify\.app$/i.test(origin)||
 /^https?:\/\/localhost(:\d+)?$/i.test(origin)||
 /^capacitor:\/\/localhost$/i.test(origin);
}

function respond(req,data,status=200){
 return json(data,status,corsHeaders(req.headers.get('Origin')));
}

async function adminOk(req,env){
 // Never trust an email supplied by the app. Verify Google's signed ID token
 // server-side and authorize only the single configured administrator.
 const authorization=req.headers.get('Authorization')||'';
 const idToken=authorization.startsWith('Bearer ')?authorization.slice(7).trim():'';
 if(!idToken)return false;
 const expectedEmail='layali.7kayat@gmail.com';
 const expectedAudience='329604815492-em2u177fg9u5k7jmje1kojvgbfb8b1ff.apps.googleusercontent.com';
 try{
  const response=await fetch('https://oauth2.googleapis.com/tokeninfo?id_token='+encodeURIComponent(idToken));
  if(!response.ok)return false;
  const claims=await response.json();
  const issuer=['accounts.google.com','https://accounts.google.com'].includes(String(claims.iss||''));
  const audience=String(claims.aud||'')===expectedAudience;
  const notExpired=Number(claims.exp)>Math.floor(Date.now()/1000);
  const verified=String(claims.email_verified)==='true';
  const isAdmin=String(claims.email||'').trim().toLowerCase()===expectedEmail;
  return Boolean(claims.sub)&&issuer&&audience&&notExpired&&verified&&isAdmin;
 }catch(_){return false;}
}

function id(){
 return crypto.randomUUID();
}

function safeFile(name){
 return String(name||'file')
  .replace(/[^a-zA-Z0-9._-]/g,'_')
  .slice(-100)||'file';
}


function b64url(input){
 const bytes=typeof input==='string'?new TextEncoder().encode(input):new Uint8Array(input);
 let binary=''; for(const b of bytes)binary+=String.fromCharCode(b);
 return btoa(binary).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
}
async function fcmAccessToken(env){
 if(!env.FCM_PROJECT_ID||!env.FCM_CLIENT_EMAIL||!env.FCM_PRIVATE_KEY)throw new Error('FCM service account secrets are not configured');
 const now=Math.floor(Date.now()/1000);
 const header=b64url(JSON.stringify({alg:'RS256',typ:'JWT'}));
 const claim=b64url(JSON.stringify({iss:env.FCM_CLIENT_EMAIL,scope:'https://www.googleapis.com/auth/firebase.messaging',aud:'https://oauth2.googleapis.com/token',iat:now,exp:now+3600}));
 const unsigned=header+'.'+claim;
 const pem=env.FCM_PRIVATE_KEY.replace(/\\n/g,'\n').replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g,'');
 const der=Uint8Array.from(atob(pem),c=>c.charCodeAt(0));
 const key=await crypto.subtle.importKey('pkcs8',der,{name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['sign']);
 const signature=await crypto.subtle.sign('RSASSA-PKCS1-v1_5',key,new TextEncoder().encode(unsigned));
 const assertion=unsigned+'.'+b64url(signature);
 const response=await fetch('https://oauth2.googleapis.com/token',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({grant_type:'urn:ietf:params:oauth:grant-type:jwt-bearer',assertion})});
 if(!response.ok)throw new Error('FCM OAuth token request failed');
 return (await response.json()).access_token;
}
async function notifyCommentReply(env,parentCommentId,storyId,storyTitle,replyText,replyName){
 const parent=await env.DB.prepare('SELECT visitor_token FROM comments WHERE id=? AND story_id=?').bind(Number(parentCommentId),Number(storyId)).first();
 const visitor=String(parent?.visitor_token||'');
 if(!visitor)return;
 const devices=await env.DB.prepare('SELECT DISTINCT fcm_token FROM push_devices WHERE visitor_id=?').bind(visitor).all();
 if(!devices.results?.length)return;
 const access=await fcmAccessToken(env);
 for(const row of devices.results){
  const response=await fetch(`https://fcm.googleapis.com/v1/projects/${env.FCM_PROJECT_ID}/messages:send`,{
   method:'POST',headers:{Authorization:`Bearer ${access}`,'Content-Type':'application/json'},
   body:JSON.stringify({message:{token:row.fcm_token,data:{type:'comment_reply',story_id:String(storyId),comment_id:String(parentCommentId),title:`رد جديد على تعليقك في ${storyTitle}`,body:`${replyName}: ${String(replyText).slice(0,120)}`},android:{priority:'HIGH'}}})
  });
  if(!response.ok){const err=await response.text();if(response.status===404||response.status===400)await env.DB.prepare('DELETE FROM push_devices WHERE visitor_id=? AND fcm_token=?').bind(visitor,row.fcm_token).run();else console.error('FCM reply send failed',response.status,err);}
 }
}

async function notifyStorySubscribers(env,storyId,storyTitle,episodeTitle){
 const rows=await env.DB.prepare('SELECT DISTINCT fcm_token FROM story_notification_subscriptions WHERE story_id=? AND enabled=1').bind(Number(storyId)).all();
 if(!rows.results?.length)return;
 const access=await fcmAccessToken(env);
 for(const row of rows.results){
  const response=await fetch(`https://fcm.googleapis.com/v1/projects/${env.FCM_PROJECT_ID}/messages:send`,{
   method:'POST',headers:{Authorization:`Bearer ${access}`,'Content-Type':'application/json'},
   body:JSON.stringify({message:{token:row.fcm_token,data:{type:'new_episode',title:'حلقة جديدة: '+storyTitle,body:episodeTitle,story_id:String(storyId)},android:{priority:'HIGH'}}})
  });
  if(!response.ok){const err=await response.text(); if(response.status===404||response.status===400){await env.DB.prepare('DELETE FROM story_notification_subscriptions WHERE story_id=? AND fcm_token=?').bind(Number(storyId),row.fcm_token).run();} else console.error('FCM send failed',response.status,err);}
 }
}

async function ensureCategories(env){
 const stmt=env.DB.prepare(
  'INSERT OR IGNORE INTO categories (name,slug,sort_order,is_active) VALUES (?,?,?,1)'
 );
 await env.DB.batch(CATEGORIES.map(c=>stmt.bind(c.title,c.slug,c.sort_order)));
 // Keep a normalized many-to-many relation while preserving category_id for older clients.
 await env.DB.prepare(`CREATE TABLE IF NOT EXISTS story_categories (
   story_id INTEGER NOT NULL, category_id INTEGER NOT NULL,
   PRIMARY KEY(story_id,category_id),
   FOREIGN KEY(story_id) REFERENCES stories(id) ON DELETE CASCADE,
   FOREIGN KEY(category_id) REFERENCES categories(id) ON DELETE CASCADE
 )`).run();
 await env.DB.prepare(`INSERT OR IGNORE INTO story_categories(story_id,category_id)
   SELECT id,category_id FROM stories WHERE category_id IS NOT NULL`).run();
}

async function attachStoryCategories(env, stories){
 const rows=(stories||[]);
 if(!rows.length)return rows.map(s=>({...s,category_ids:[String(s.category_id)],category_titles:[]}));
 const links=await env.DB.prepare(`SELECT sc.story_id,sc.category_id,c.name AS category_title
   FROM story_categories sc JOIN categories c ON c.id=sc.category_id
   ORDER BY c.sort_order,c.id`).all();
 const map=new Map();
 for(const row of links.results||[]){
  const key=String(row.story_id); if(!map.has(key))map.set(key,{ids:[],titles:[]});
  map.get(key).ids.push(String(row.category_id)); map.get(key).titles.push(row.category_title);
 }
 return rows.map(s=>{
  const related=map.get(String(s.id));
  const fallback=String(s.category_id||'');
  return {...s,category_ids:related?.ids?.length?related.ids:(fallback?[fallback]:[]),category_titles:related?.titles||[]};
 });
}

async function saveStoryCategories(env, storyId, categoryIds){
 const ids=[...new Set((categoryIds||[]).map(Number).filter(n=>Number.isInteger(n)&&n>0))];
 if(!ids.length)throw new Error('اختاري تصنيفًا واحدًا على الأقل');
 const placeholders=ids.map(()=>'?').join(',');
 const found=await env.DB.prepare(`SELECT id FROM categories WHERE is_active=1 AND id IN (${placeholders})`).bind(...ids).all();
 if((found.results||[]).length!==ids.length)throw new Error('أحد التصنيفات المختارة غير موجود');
 await env.DB.prepare('DELETE FROM story_categories WHERE story_id=?').bind(Number(storyId)).run();
 await env.DB.batch(ids.map(cid=>env.DB.prepare('INSERT OR IGNORE INTO story_categories(story_id,category_id) VALUES(?,?)').bind(Number(storyId),cid)));
}

async function categoryIdsFromBody(env,b){
 const supplied=Array.isArray(b.category_ids)?b.category_ids:(b.category_id?[b.category_id]:[]);
 const ids=[...new Set(supplied.map(Number).filter(n=>Number.isInteger(n)&&n>0))];
 if(!ids.length)throw new Error('اختاري تصنيفًا واحدًا على الأقل');
 const marks=ids.map(()=>'?').join(',');
 const r=await env.DB.prepare(`SELECT id FROM categories WHERE id IN (${marks}) AND is_active=1`).bind(...ids).all();
 if((r.results||[]).length!==ids.length)throw new Error('أحد التصنيفات المختارة غير موجود');
 return ids;
}

async function catalog(env){
 await ensureCategories(env);
 const [cats,stories]=await Promise.all([
  env.DB.prepare('SELECT id,name AS title,slug,NULL AS emoji,sort_order,image_url AS image_path FROM categories WHERE is_active=1 ORDER BY sort_order,id').all(),
  env.DB.prepare('SELECT id,title,description,category_id,audio_url AS audio_path,image_url AS image_path,wide_image_url AS wide_image_path,suggested_image_url AS suggested_image_path,total_episodes,status,character_one_name,character_two_name,character_three_name,character_one_image_url AS character_one_image_path,character_two_image_url AS character_two_image_path,character_three_image_url AS character_three_image_path,views,created_at FROM stories WHERE is_published=1 ORDER BY created_at DESC').all()
 ]);
 const enriched=await attachStoryCategories(env,stories.results||[]);
 return {
  categories:(cats.results||[]).map(c=>({...c,id:String(c.id),emoji:CATEGORIES.find(x=>x.slug===c.slug)?.emoji||'📚'})),
  stories:enriched.map(s=>({...s,id:String(s.id),category_id:String(s.category_id),category_ids:s.category_ids.map(String)}))
 };
}

// AdMob rewarded SSV verification. Google rotates these public keys; cache in-memory
// for at most 24 hours and verify the original, unmodified query string.
let admobSsvKeysCache=null;
let admobSsvKeysFetchedAt=0;
const ADMOB_SSV_KEYS_URL='https://www.gstatic.com/admob/reward/verifier-keys.json';
// AdMob SSV sends the numeric ad unit ID (the part after the slash), not the full SDK ID.
const DRAMIO_REWARDED_AD_UNIT='2960783714';

function decodeBase64Url(value){
 const normalized=String(value||'').replace(/-/g,'+').replace(/_/g,'/');
 const padded=normalized+'='.repeat((4-normalized.length%4)%4);
 const binary=atob(padded);
 return Uint8Array.from(binary,c=>c.charCodeAt(0));
}
function derEcdsaToRaw(der){
 let i=0;
 if(der[i++]!==0x30)throw new Error('Invalid ECDSA DER sequence');
 let seqLen=der[i++];
 if(seqLen&0x80){const n=seqLen&0x7f;seqLen=0;for(let j=0;j<n;j++)seqLen=(seqLen<<8)|der[i++];}
 if(der[i++]!==0x02)throw new Error('Invalid ECDSA r');
 let rLen=der[i++];let r=der.slice(i,i+rLen);i+=rLen;
 if(der[i++]!==0x02)throw new Error('Invalid ECDSA s');
 let sLen=der[i++];let ss=der.slice(i,i+sLen);
 const trim=x=>{let j=0;while(j<x.length-32&&x[j]===0)j++;x=x.slice(j);if(x.length>32)throw new Error('Invalid ECDSA scalar');const out=new Uint8Array(32);out.set(x,32-x.length);return out;};
 const raw=new Uint8Array(64);raw.set(trim(r),0);raw.set(trim(ss),32);return raw;
}
async function getAdmobSsvKeys(){
 if(admobSsvKeysCache&&Date.now()-admobSsvKeysFetchedAt<23*60*60*1000)return admobSsvKeysCache;
 const response=await fetch(ADMOB_SSV_KEYS_URL,{headers:{'Accept':'application/json'}});
 if(!response.ok)throw new Error('Unable to download AdMob verification keys');
 const data=await response.json();
 if(!Array.isArray(data.keys)||!data.keys.length)throw new Error('AdMob verification keys are empty');
 admobSsvKeysCache=new Map(data.keys.map(k=>[String(k.keyId),k.base64]));
 admobSsvKeysFetchedAt=Date.now();
 return admobSsvKeysCache;
}
async function verifyAdmobSsvRequest(requestUrl){
 const question=requestUrl.indexOf('?');
 if(question<0)throw new Error('Missing SSV query');
 const rawQuery=requestUrl.slice(question+1);
 const marker=rawQuery.lastIndexOf('&signature=');
 if(marker<0||!rawQuery.endsWith('&key_id='+new URL(requestUrl).searchParams.get('key_id')))throw new Error('Invalid SSV parameter order');
 const signedText=rawQuery.slice(0,marker);
 const tail=rawQuery.slice(marker+1);
 const tailParams=new URLSearchParams(tail);
 const signature=tailParams.get('signature');
 const keyId=tailParams.get('key_id');
 if(!signature||!keyId||tailParams.getAll('signature').length!==1||tailParams.getAll('key_id').length!==1)throw new Error('Missing SSV signature or key ID');
 const keys=await getAdmobSsvKeys();
 const keyBase64=keys.get(String(keyId));
 if(!keyBase64)throw new Error('Unknown AdMob key ID');
 const publicKey=await crypto.subtle.importKey('spki',decodeBase64Url(keyBase64),{name:'ECDSA',namedCurve:'P-256'},false,['verify']);
 const signatureBytes=derEcdsaToRaw(decodeBase64Url(signature));
 return await crypto.subtle.verify({name:'ECDSA',hash:'SHA-256'},publicKey,signatureBytes,new TextEncoder().encode(signedText));
}

async function resolveEngagementVisitor(env, visitorInput){
 const input=String(visitorInput||'').trim();
 if(!/^[a-zA-Z0-9-]{20,80}$/.test(input))return '';
 const alias=await env.DB.prepare('SELECT account_visitor_id FROM engagement_identity_links WHERE visitor_id=?').bind(input).first();
 return String(alias?.account_visitor_id||input);
}

async function mergeVisitorData(env, fromVisitor, toVisitor){
 if(!fromVisitor||!toVisitor||fromVisitor===toVisitor)return;
 // Ensure the destination profile exists before transferring the guest profile.
 await env.DB.prepare(`INSERT INTO engagement_profiles(visitor_id,display_name) VALUES(?,'مستخدم Dramio') ON CONFLICT(visitor_id) DO NOTHING`).bind(toVisitor).run();
 await env.DB.prepare(`UPDATE engagement_profiles SET xp=xp+COALESCE((
   SELECT MAX(0,guest.xp-COALESCE((
    SELECT SUM(ge.points) FROM engagement_events ge
    WHERE ge.visitor_id=? AND EXISTS(SELECT 1 FROM engagement_events ae WHERE ae.visitor_id=? AND ae.event_key=ge.event_key)
   ),0)) FROM engagement_profiles guest WHERE guest.visitor_id=?
  ),0),last_seen=CURRENT_TIMESTAMP WHERE visitor_id=?`).bind(fromVisitor,toVisitor,fromVisitor,toVisitor).run();
 await env.DB.batch([
  env.DB.prepare(`INSERT OR IGNORE INTO engagement_events(visitor_id,event_key,event_type,points,created_at) SELECT ?,event_key,event_type,points,created_at FROM engagement_events WHERE visitor_id=?`).bind(toVisitor,fromVisitor),
  env.DB.prepare('UPDATE admob_ssv_transactions SET visitor_id=? WHERE visitor_id=?').bind(toVisitor,fromVisitor),
  env.DB.prepare('UPDATE comments SET visitor_token=? WHERE visitor_token=?').bind(toVisitor,fromVisitor),
  env.DB.prepare(`INSERT OR IGNORE INTO comment_likes(comment_id,visitor_token,created_at) SELECT comment_id,?,created_at FROM comment_likes WHERE visitor_token=?`).bind(toVisitor,fromVisitor),
  env.DB.prepare(`INSERT OR IGNORE INTO character_votes(story_id,character_id,voter_id,vote,updated_at) SELECT story_id,character_id,?,vote,updated_at FROM character_votes WHERE voter_id=?`).bind(toVisitor,fromVisitor),
  env.DB.prepare(`INSERT OR IGNORE INTO push_devices(visitor_id,fcm_token,updated_at) SELECT ?,fcm_token,updated_at FROM push_devices WHERE visitor_id=?`).bind(toVisitor,fromVisitor),
  env.DB.prepare(`INSERT OR IGNORE INTO story_notification_subscriptions(visitor_id,story_id,fcm_token,enabled,updated_at) SELECT ?,story_id,fcm_token,enabled,updated_at FROM story_notification_subscriptions WHERE visitor_id=?`).bind(toVisitor,fromVisitor)
 ]);
 await env.DB.batch([
  env.DB.prepare('DELETE FROM comment_likes WHERE visitor_token=?').bind(fromVisitor),
  env.DB.prepare('DELETE FROM character_votes WHERE voter_id=?').bind(fromVisitor),
  env.DB.prepare('DELETE FROM push_devices WHERE visitor_id=?').bind(fromVisitor),
  env.DB.prepare('DELETE FROM story_notification_subscriptions WHERE visitor_id=?').bind(fromVisitor),
  env.DB.prepare('DELETE FROM engagement_profiles WHERE visitor_id=?').bind(fromVisitor),
  env.DB.prepare('UPDATE engagement_identity_links SET account_visitor_id=? WHERE account_visitor_id=?').bind(toVisitor,fromVisitor),
  env.DB.prepare(`INSERT INTO engagement_identity_links(visitor_id,account_visitor_id) VALUES(?,?) ON CONFLICT(visitor_id) DO UPDATE SET account_visitor_id=excluded.account_visitor_id`).bind(fromVisitor,toVisitor)
 ]);
}

export default {
 async fetch(req,env){
  const origin=req.headers.get('Origin');

  if(!validOrigin(origin)){
   return json({error:'Origin not allowed'},403);
  }

  if(req.method==='OPTIONS'){
   return new Response(null,{
    status:204,
    headers:corsHeaders(origin)
   });
  }

  const u=new URL(req.url);
  const p=u.pathname;

  try{

   // AdMob calls this endpoint directly after a rewarded ad is completed.
   // Never grant XP from the Android client callback; only this signed callback can do so.
   if(p==='/api/admob/ssv'&&req.method==='GET'){
    // Safe SSV diagnostics: never log user_id, transaction_id, signature, or full callback URL.
    console.info('AdMob SSV received',{method:req.method,hasOrigin:Boolean(origin)});
    try{
     const validSignature=await verifyAdmobSsvRequest(req.url);
     if(!validSignature){
      console.warn('AdMob SSV rejected: invalid signature');
      return new Response('Invalid signature',{status:403});
     }
     console.info('AdMob SSV signature verified');
     const q=u.searchParams;
     const visitorInput=String(q.get('user_id')||'').trim();
     const transactionId=String(q.get('transaction_id')||'').trim();
     const adUnitRaw=String(q.get('ad_unit')||'').trim();
     const adUnit=adUnitRaw.includes('/')?adUnitRaw.split('/').pop():adUnitRaw;
     const rewardAmount=Number(q.get('reward_amount'));
     const rewardItem=String(q.get('reward_item')||'').trim();
     console.info('AdMob SSV parameters received',{
      hasUserId:Boolean(visitorInput),transactionIdPresent:Boolean(transactionId),
      adUnitMatches:adUnit===DRAMIO_REWARDED_AD_UNIT,rewardAmount,rewardItem
     });
     if(!transactionId||transactionId.length>160||
        adUnit!==DRAMIO_REWARDED_AD_UNIT||rewardAmount!==10||rewardItem!=='XP'){
      console.error('AdMob SSV reward parameters rejected',{
       hasUserId:Boolean(visitorInput),transactionIdValid:Boolean(transactionId&&transactionId.length<=160),
       adUnitMatches:adUnit===DRAMIO_REWARDED_AD_UNIT,rewardAmount,rewardItem
      });
      return new Response('Invalid reward parameters',{status:400});
     }
     // AdMob's URL verification form makes user_id optional. Accept a valid
     // signed callback without a user ID, but never grant XP without an owner.
     if(!visitorInput){
      console.warn('AdMob SSV accepted but no user_id; XP not assigned');
      return new Response('OK - no user_id; no reward assigned',{status:200});
     }
     if(!/^[a-zA-Z0-9._-]{1,128}$/.test(visitorInput)){
      console.error('AdMob SSV user_id format rejected');
      return new Response('Invalid user_id',{status:400});
     }
     await env.DB.batch([
      env.DB.prepare(`CREATE TABLE IF NOT EXISTS engagement_profiles(
       visitor_id TEXT PRIMARY KEY,display_name TEXT NOT NULL DEFAULT 'مستخدم Dramio',
       avatar_key TEXT NOT NULL DEFAULT '',xp INTEGER NOT NULL DEFAULT 0,
       is_member INTEGER NOT NULL DEFAULT 0,last_seen TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
      )`),
      env.DB.prepare(`CREATE TABLE IF NOT EXISTS admob_ssv_transactions(
       transaction_id TEXT PRIMARY KEY,visitor_id TEXT NOT NULL,reward_amount INTEGER NOT NULL,
       reward_item TEXT NOT NULL,applied INTEGER NOT NULL DEFAULT 0,
       created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
      )`),
      env.DB.prepare(`CREATE TABLE IF NOT EXISTS engagement_identity_links(
       visitor_id TEXT PRIMARY KEY,account_visitor_id TEXT NOT NULL,
       created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
      )`)
     ]);
     const alias=await env.DB.prepare('SELECT account_visitor_id FROM engagement_identity_links WHERE visitor_id=?').bind(visitorInput).first();
     const visitor=String(alias?.account_visitor_id||visitorInput);
     console.info('AdMob SSV user_id format accepted');
     const batchResult=await env.DB.batch([
      env.DB.prepare(`INSERT INTO engagement_profiles(visitor_id,display_name) VALUES(?,'مستخدم Dramio')
       ON CONFLICT(visitor_id) DO NOTHING`).bind(visitor),
      env.DB.prepare(`INSERT OR IGNORE INTO admob_ssv_transactions(transaction_id,visitor_id,reward_amount,reward_item)
       VALUES(?,?,?,?)`).bind(transactionId,visitor,rewardAmount,rewardItem),
      env.DB.prepare(`UPDATE engagement_profiles SET xp=xp+10,last_seen=CURRENT_TIMESTAMP
       WHERE visitor_id=? AND EXISTS(
        SELECT 1 FROM admob_ssv_transactions WHERE transaction_id=? AND visitor_id=? AND applied=0
       )`).bind(visitor,transactionId,visitor),
      env.DB.prepare(`UPDATE admob_ssv_transactions SET applied=1
       WHERE transaction_id=? AND visitor_id=? AND applied=0`).bind(transactionId,visitor)
     ]);
     const appliedRow=await env.DB.prepare('SELECT applied FROM admob_ssv_transactions WHERE transaction_id=?').bind(transactionId).first();
     console.info('AdMob SSV database processing completed',{
      transactionApplied:appliedRow?.applied===1,
      batchStatements:batchResult?.length||0
     });
     return new Response('OK',{status:200,headers:{'Cache-Control':'no-store','Content-Type':'text/plain; charset=utf-8'}});
    }catch(error){
     console.error('AdMob SSV verification failed:',error?.message||error);
     return new Response('SSV verification failed',{status:500});
    }
   }

   if(p==='/'&&req.method==='GET'){
    return respond(req,{
     name:'قصص وروايات صوتية',
     status:'online',
     health:'/api/health'
    });
   }

   // Google Sign-In: verify the ID token with Google's tokeninfo endpoint,
   // then create/update the account in the existing Cloudflare D1 database.
   if(p==='/api/auth/google'&&req.method==='POST'){
    const body=await req.json();
    const idToken=String(body.id_token||'').trim();
    const displayName=String(body.display_name||'').trim().slice(0,40);
    if(!idToken||displayName.length<2){
     return respond(req,{error:'رمز Google واسم مستخدم صحيح مطلوبان'},400);
    }
    // TEMPORARY DIAGNOSTICS: never log or return the ID token, email, or Google subject.
    // Google documents tokeninfo as a debugging endpoint; replace with local JWKS
    // signature verification before treating this as a final production design.
    const tokenInfoResponse=await fetch('https://oauth2.googleapis.com/tokeninfo?id_token='+encodeURIComponent(idToken));
    if(!tokenInfoResponse.ok){
     return respond(req,{
      error:'تعذر التحقق من رمز Google',
      diagnostic:{stage:'google_tokeninfo',google_http_status:tokenInfoResponse.status}
     },401);
    }
    let tokenInfo;
    try { tokenInfo=await tokenInfoResponse.json(); }
    catch (_) {
     return respond(req,{error:'تعذر قراءة استجابة Google',diagnostic:{stage:'google_tokeninfo_json'}},502);
    }
    const expectedAudience='329604815492-em2u177fg9u5k7jmje1kojvgbfb8b1ff.apps.googleusercontent.com';
    const validIssuer=['accounts.google.com','https://accounts.google.com'].includes(String(tokenInfo.iss||''));
    const validAudience=String(tokenInfo.aud||'')===expectedAudience;
    const validExpiry=Number(tokenInfo.exp)>Math.floor(Date.now()/1000);
    const validEmail=Boolean(tokenInfo.email)&&String(tokenInfo.email_verified)==='true';
    const validSubject=Boolean(tokenInfo.sub);
    if(!validIssuer||!validAudience||!validExpiry||!validEmail||!validSubject){
     return respond(req,{
      error:'تعذر التحقق من هوية حساب Google',
      diagnostic:{
       stage:'google_claim_validation',
       checks:{issuer:validIssuer,audience:validAudience,expiry:validExpiry,email_verified:validEmail,subject:validSubject},
       received:{issuer:String(tokenInfo.iss||''),audience:String(tokenInfo.aud||''),authorized_party:String(tokenInfo.azp||'')},
       expected_audience:expectedAudience
      }
     },401);
    }
    await env.DB.batch([
     env.DB.prepare(`CREATE TABLE IF NOT EXISTS users (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      google_sub TEXT NOT NULL UNIQUE,
      email TEXT NOT NULL UNIQUE,
      display_name TEXT NOT NULL,
      avatar_key TEXT NOT NULL DEFAULT 'dramio-default',
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
     )`),
     env.DB.prepare(`CREATE TABLE IF NOT EXISTS engagement_profiles(
      visitor_id TEXT PRIMARY KEY,display_name TEXT NOT NULL DEFAULT 'مستخدم Dramio',
      avatar_key TEXT NOT NULL DEFAULT '',xp INTEGER NOT NULL DEFAULT 0,
      is_member INTEGER NOT NULL DEFAULT 0,last_seen TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
     )`),
     env.DB.prepare(`CREATE TABLE IF NOT EXISTS engagement_events(
      visitor_id TEXT NOT NULL,event_key TEXT NOT NULL,event_type TEXT NOT NULL,
      points INTEGER NOT NULL,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY(visitor_id,event_key)
     )`),
     env.DB.prepare(`CREATE TABLE IF NOT EXISTS admob_ssv_transactions(
      transaction_id TEXT PRIMARY KEY,visitor_id TEXT NOT NULL,reward_amount INTEGER NOT NULL,
      reward_item TEXT NOT NULL,applied INTEGER NOT NULL DEFAULT 0,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
     )`),
     env.DB.prepare(`CREATE TABLE IF NOT EXISTS engagement_identity_links(
      visitor_id TEXT PRIMARY KEY,account_visitor_id TEXT NOT NULL,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
     )`),
     env.DB.prepare(`CREATE TABLE IF NOT EXISTS engagement_google_links(
      google_sub TEXT PRIMARY KEY,visitor_id TEXT NOT NULL,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
     )`),
     env.DB.prepare(`CREATE INDEX IF NOT EXISTS idx_engagement_google_visitor ON engagement_google_links(visitor_id)`)
    ]);
    await env.DB.prepare(`INSERT INTO users(google_sub,email,display_name)
     VALUES(?,?,?) ON CONFLICT(google_sub) DO UPDATE SET
     email=excluded.email, display_name=excluded.display_name,
     updated_at=CURRENT_TIMESTAMP`).bind(String(tokenInfo.sub),String(tokenInfo.email).toLowerCase(),displayName).run();
    const user=await env.DB.prepare(`SELECT google_sub,email,display_name,avatar_key,created_at
     FROM users WHERE google_sub=?`).bind(String(tokenInfo.sub)).first();

    // Keep a device's existing Dramio identity. A Google subject is linked to that
    // identity; if this Google account was already used on another device, merge
    // the current device data into its existing canonical identity instead.
    const suppliedVisitorId=String(body.visitor_id||'').trim();
    if(suppliedVisitorId && !/^[a-zA-Z0-9-]{20,80}$/.test(suppliedVisitorId)){
     return respond(req,{error:'معرّف الزائر غير صالح'},400);
    }
    const linkedGoogle=await env.DB.prepare('SELECT visitor_id FROM engagement_google_links WHERE google_sub=?').bind(String(tokenInfo.sub)).first();
    const currentVisitorId=suppliedVisitorId ? await resolveEngagementVisitor(env,suppliedVisitorId) : '';
    const legacyAccountVisitorId='g-'+String(tokenInfo.sub);
    const legacyProfile=await env.DB.prepare('SELECT visitor_id FROM engagement_profiles WHERE visitor_id=?').bind(legacyAccountVisitorId).first();
    const accountVisitorId=String(linkedGoogle?.visitor_id||(legacyProfile?legacyAccountVisitorId:'')||currentVisitorId||legacyAccountVisitorId);
    await env.DB.prepare(`INSERT INTO engagement_profiles(visitor_id,display_name) VALUES(?,?)
     ON CONFLICT(visitor_id) DO UPDATE SET
      display_name=CASE WHEN engagement_profiles.display_name='مستخدم Dramio' THEN excluded.display_name ELSE engagement_profiles.display_name END,
      last_seen=CURRENT_TIMESTAMP`).bind(accountVisitorId,displayName).run();
    if(currentVisitorId && currentVisitorId!==accountVisitorId){
     await mergeVisitorData(env,currentVisitorId,accountVisitorId);
    }
    await env.DB.prepare(`INSERT INTO engagement_google_links(google_sub,visitor_id) VALUES(?,?)
     ON CONFLICT(google_sub) DO NOTHING`).bind(String(tokenInfo.sub),accountVisitorId).run();
    const accountProfile=await env.DB.prepare('SELECT xp FROM engagement_profiles WHERE visitor_id=?').bind(accountVisitorId).first();
    return respond(req,{success:true,user,visitor_id:accountVisitorId,xp:Number(accountProfile?.xp||0)});
   }

   if(p==='/api/profile/avatar'&&req.method==='POST'){
    const visitor=String(u.searchParams.get('visitor_id')||'').trim();
    const type=req.headers.get('Content-Type')||'';
    if(!/^[a-zA-Z0-9-]{20,80}$/.test(visitor))return respond(req,{error:'معرّف المستخدم غير صالح'},400);
    if(!['image/jpeg','image/png','image/webp'].includes(type))return respond(req,{error:'صيغة الصورة غير مدعومة'},415);
    const bytes=await req.arrayBuffer();
    if(bytes.byteLength<1||bytes.byteLength>5*1024*1024)return respond(req,{error:'حجم الصورة يجب ألا يتجاوز 5 ميغابايت'},413);
    const ext=type==='image/jpeg'?'jpg':type==='image/png'?'png':'webp';
    const key=`images/avatars/${visitor}.${ext}`;
    await env.MEDIA.put(key,bytes,{httpMetadata:{contentType:type,cacheControl:'public, max-age=86400'}});
    return respond(req,{success:true,key});
   }

   // Public profile update: persist the display name and avatar in D1 immediately,
   // so every device sees the same profile without waiting for the next XP event.
   if(p==='/api/profile/update'&&req.method==='POST'){
    const b=await req.json().catch(()=>({}));
    const visitorInput=String(b.visitor_id||'').trim();
    const displayName=String(b.display_name||'').replace(/[<>\u0000-\u001F]/g,'').trim().slice(0,40);
    const avatarKey=String(b.avatar_key||'').trim();
    if(!/^[a-zA-Z0-9-]{20,80}$/.test(visitorInput))return respond(req,{error:'معرّف المستخدم غير صالح'},400);
    if(!displayName)return respond(req,{error:'يرجى كتابة اسم صالح'},400);
    const visitor=await resolveEngagementVisitor(env,visitorInput);
    if(!visitor)return respond(req,{error:'تعذر تحديد حساب المستخدم'},400);
    let safeAvatar='';
    if(avatarKey){
     const match=avatarKey.match(/^images\/avatars\/([a-zA-Z0-9-]{20,80})\.(jpg|png|webp)$/);
     if(!match||![visitorInput,visitor].includes(match[1]))return respond(req,{error:'الصورة لا تخص هذا الحساب'},400);
     const object=await env.MEDIA.head(avatarKey);
     if(!object)return respond(req,{error:'لم يتم العثور على الصورة المرفوعة'},404);
     safeAvatar=avatarKey;
    }
    await env.DB.prepare(`CREATE TABLE IF NOT EXISTS engagement_profiles(
      visitor_id TEXT PRIMARY KEY,display_name TEXT NOT NULL DEFAULT 'مستخدم Dramio',
      avatar_key TEXT NOT NULL DEFAULT '',xp INTEGER NOT NULL DEFAULT 0,
      is_member INTEGER NOT NULL DEFAULT 0,last_seen TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    )`).run();
    // Ensure the profile row exists, then explicitly update the canonical visitor row.
    // Keeping INSERT and UPDATE separate makes the intended name write unambiguous.
    await env.DB.prepare(`INSERT OR IGNORE INTO engagement_profiles
      (visitor_id,display_name,avatar_key) VALUES(?,?,?)`)
      .bind(visitor,displayName,safeAvatar).run();
    const updateResult=await env.DB.prepare(`UPDATE engagement_profiles
      SET display_name=?,
          avatar_key=CASE WHEN ?<>'' THEN ? ELSE avatar_key END,
          last_seen=CURRENT_TIMESTAMP
      WHERE visitor_id=?`)
      .bind(displayName,safeAvatar,safeAvatar,visitor).run();
    if(!Number(updateResult.meta?.changes||0)){
      return respond(req,{error:'لم يتم تحديث الملف الشخصي في قاعدة البيانات'},500);
    }
    const profile=await env.DB.prepare('SELECT visitor_id,display_name,avatar_key,xp FROM engagement_profiles WHERE visitor_id=?').bind(visitor).first();
    if(!profile || profile.display_name!==displayName){
      return respond(req,{error:'تعذر تأكيد حفظ الاسم الجديد'},500);
    }
    return respond(req,{success:true,profile});
   }

   if(p==='/api/notifications/register'&&req.method==='POST'){
    const b=await req.json().catch(()=>({}));
    const visitor=String(b.visitor_id||'').trim().slice(0,100);
    const token=String(b.fcm_token||'').trim().slice(0,4096);
    if(!/^[a-zA-Z0-9-]{20,80}$/.test(visitor)||!token)return respond(req,{error:'بيانات الجهاز غير مكتملة'},400);
    await env.DB.prepare(`CREATE TABLE IF NOT EXISTS push_devices(
      id INTEGER PRIMARY KEY AUTOINCREMENT,visitor_id TEXT NOT NULL,fcm_token TEXT NOT NULL,
      updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,UNIQUE(visitor_id,fcm_token)
    )`).run();
    await env.DB.prepare(`INSERT INTO push_devices(visitor_id,fcm_token) VALUES(?,?)
      ON CONFLICT(visitor_id,fcm_token) DO UPDATE SET updated_at=CURRENT_TIMESTAMP`).bind(visitor,token).run();
    return respond(req,{success:true});
   }

   if(p==='/api/notifications/subscribe'&&req.method==='POST'){
    const b=await req.json();
    const visitor=String(b.visitor_id||'').trim().slice(0,100);
    const storyId=Number(b.story_id);
    const token=String(b.fcm_token||'').trim().slice(0,4096);
    if(!visitor||!Number.isInteger(storyId)||storyId<1||!token)return respond(req,{error:'بيانات الاشتراك غير مكتملة'},400);
    await env.DB.prepare(`CREATE TABLE IF NOT EXISTS story_notification_subscriptions(
      id INTEGER PRIMARY KEY AUTOINCREMENT, visitor_id TEXT NOT NULL, story_id INTEGER NOT NULL,
      fcm_token TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1,
      updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
      UNIQUE(visitor_id,story_id,fcm_token)
    )`).run();
    if(b.enabled===false){
      await env.DB.prepare('UPDATE story_notification_subscriptions SET enabled=0,updated_at=CURRENT_TIMESTAMP WHERE visitor_id=? AND story_id=? AND fcm_token=?').bind(visitor,storyId,token).run();
    }else{
      const story=await env.DB.prepare('SELECT id FROM stories WHERE id=? AND is_published=1').bind(storyId).first();
      if(!story)return respond(req,{error:'القصة غير موجودة'},404);
      await env.DB.prepare(`INSERT INTO story_notification_subscriptions(visitor_id,story_id,fcm_token,enabled)
        VALUES(?,?,?,1) ON CONFLICT(visitor_id,story_id,fcm_token) DO UPDATE SET enabled=1,updated_at=CURRENT_TIMESTAMP`).bind(visitor,storyId,token).run();
    }
    return respond(req,{success:true,enabled:b.enabled!==false});
   }

// Gamification: server-side XP awards with idempotent event keys.
   if(p==='/api/xp/award'&&req.method==='POST'){
    const b=await req.json().catch(()=>({}));
    const visitorInput=String(b.visitor_id||'').trim();
    const displayName=String(b.display_name||'مستخدم Dramio').trim().slice(0,40)||'مستخدم Dramio';
    const avatarKey=String(b.avatar_key||'').trim();
    const event=String(b.event||'');
    const eventKey=String(b.event_key||'').trim().slice(0,160);
    if(!/^[a-zA-Z0-9-]{20,80}$/.test(visitorInput)||!eventKey)return respond(req,{error:'بيانات التفاعل غير صالحة'},400);
    const visitor=await resolveEngagementVisitor(env,visitorInput);
    if(event==='rewarded_ad')return respond(req,{error:'مكافآت الإعلانات تُعتمد عبر تحقق AdMob SSV فقط'},403);
    const rewards={daily_login:10,episode_complete:15,comment:5};
    const points=rewards[event];
    if(!points)return respond(req,{error:'نوع النشاط غير مدعوم'},400);
    if(event==='daily_login'&&!/^daily:\d{4}-\d{2}-\d{2}$/.test(eventKey))return respond(req,{error:'مفتاح اليوم غير صالح'},400);
    if(event==='comment'&&!/^comment:\d{4}-\d{2}-\d{2}$/.test(eventKey))return respond(req,{error:'مفتاح التعليق غير صالح'},400);
    if(event==='episode_complete'){
      const match=eventKey.match(/^episode:(\d+):(.+)$/);
      if(!match)return respond(req,{error:'الحلقة غير صالحة'},400);
      const storyId=Number(match[1]), episodeId=String(match[2]);
      const story=await env.DB.prepare('SELECT id FROM stories WHERE id=? AND is_published=1').bind(storyId).first();
      if(!story)return respond(req,{error:'القصة غير موجودة'},404);
      if(episodeId!==`main-${storyId}`){
       const part=await env.DB.prepare('SELECT id FROM story_parts WHERE id=? AND story_id=? AND is_published=1').bind(Number(episodeId),storyId).first();
       if(!part)return respond(req,{error:'الحلقة غير موجودة'},404);
      }
    }
    await env.DB.prepare(`CREATE TABLE IF NOT EXISTS engagement_profiles(
      visitor_id TEXT PRIMARY KEY,display_name TEXT NOT NULL DEFAULT 'مستخدم Dramio',
      avatar_key TEXT NOT NULL DEFAULT '',xp INTEGER NOT NULL DEFAULT 0,
      is_member INTEGER NOT NULL DEFAULT 0,last_seen TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    )`).run();
    await env.DB.prepare(`CREATE TABLE IF NOT EXISTS engagement_events(
      visitor_id TEXT NOT NULL,event_key TEXT NOT NULL,event_type TEXT NOT NULL,
      points INTEGER NOT NULL,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY(visitor_id,event_key)
    )`).run();
    const safeAvatar=/^images\/avatars\/[a-zA-Z0-9._-]+$/.test(avatarKey)?avatarKey:'';
    // XP events must never overwrite a profile name already chosen by the user.
    // The profile editor (/api/profile/update) is the authoritative source for names.
    await env.DB.prepare(`INSERT INTO engagement_profiles(visitor_id,display_name,avatar_key)
      VALUES(?,?,?) ON CONFLICT(visitor_id) DO UPDATE SET
      display_name=CASE
        WHEN engagement_profiles.display_name IS NULL OR engagement_profiles.display_name=''
          OR engagement_profiles.display_name='مستخدم Dramio'
          THEN excluded.display_name
        ELSE engagement_profiles.display_name END,
      avatar_key=CASE WHEN excluded.avatar_key<>'' THEN excluded.avatar_key ELSE engagement_profiles.avatar_key END,
      last_seen=CURRENT_TIMESTAMP`).bind(visitor,displayName,safeAvatar).run();
    const exists=await env.DB.prepare('SELECT points FROM engagement_events WHERE visitor_id=? AND event_key=?').bind(visitor,eventKey).first();
    let awarded=0;
    if(!exists){
      await env.DB.prepare('INSERT INTO engagement_events(visitor_id,event_key,event_type,points) VALUES(?,?,?,?)').bind(visitor,eventKey,event,points).run();
      await env.DB.prepare('UPDATE engagement_profiles SET xp=xp+? WHERE visitor_id=?').bind(points,visitor).run();
      awarded=points;
    }
    const profile=await env.DB.prepare('SELECT xp FROM engagement_profiles WHERE visitor_id=?').bind(visitor).first();
    return respond(req,{success:true,awarded,xp:Number(profile?.xp||0)});
   }

   if(p==='/api/xp/leaderboard'&&req.method==='GET'){
    const visitorInput=String(u.searchParams.get('visitor_id')||'').trim();
    if(!/^[a-zA-Z0-9-]{20,80}$/.test(visitorInput))return respond(req,{error:'معرّف المستخدم غير صالح'},400);
    await env.DB.prepare(`CREATE TABLE IF NOT EXISTS engagement_identity_links(
      visitor_id TEXT PRIMARY KEY,account_visitor_id TEXT NOT NULL,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    )`).run();
    const visitorAlias=await env.DB.prepare('SELECT account_visitor_id FROM engagement_identity_links WHERE visitor_id=?').bind(visitorInput).first();
    const visitor=String(visitorAlias?.account_visitor_id||visitorInput);
    await env.DB.prepare(`CREATE TABLE IF NOT EXISTS engagement_profiles(
      visitor_id TEXT PRIMARY KEY,display_name TEXT NOT NULL DEFAULT 'مستخدم Dramio',
      avatar_key TEXT NOT NULL DEFAULT '',xp INTEGER NOT NULL DEFAULT 0,
      is_member INTEGER NOT NULL DEFAULT 0,last_seen TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    )`).run();
    const rows=await env.DB.prepare(`SELECT visitor_id,display_name,avatar_key,xp,is_member,
      ROW_NUMBER() OVER(ORDER BY xp DESC,last_seen ASC) AS rank
      FROM engagement_profiles WHERE display_name<>'مستخدم Dramio' ORDER BY xp DESC,last_seen ASC LIMIT 100`).all();
    const me=await env.DB.prepare(`SELECT visitor_id,display_name,avatar_key,xp,is_member,rank FROM (
      SELECT visitor_id,display_name,avatar_key,xp,is_member,
       ROW_NUMBER() OVER(ORDER BY xp DESC,last_seen ASC) AS rank
      FROM engagement_profiles WHERE display_name<>'مستخدم Dramio'
     ) WHERE visitor_id=?`).bind(visitor).first();
    const users=(rows.results||[]).map(row=>({...row,xp:Number(row.xp||0),rank:Number(row.rank||0),is_me:row.visitor_id===visitor}));
    if(me && !users.some(row=>row.visitor_id===visitor))users.push({...me,xp:Number(me.xp||0),rank:Number(me.rank||0),is_me:true});
    return respond(req,{users,my_xp:Number(me?.xp||0),my_rank:Number(me?.rank||0)});
   }

   if(p==='/api/health'&&req.method==='GET'){
    const r=await env.DB.prepare(
     "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"
    ).all();

    return respond(req,{
     success:true,
     message:'API is connected',
     database:'connected',
     tables:(r.results||[]).map(x=>x.name),
     storage:'binding configured'
    });
   }

   if(p==='/api/catalog'&&req.method==='GET'){
    return respond(req,await catalog(env));
   }

   // Latest updates are published episodes, not demo stories. The original
   // story audio is episode 1; story_parts part_number 1 is episode 2.
   if(p==='/api/latest-updates'&&req.method==='GET'){
    await ensureCategories(env);
    const r=await env.DB.prepare(`
      SELECT story_id,story_title,category_title,image_path,episode_number,total_episodes,published_at
      FROM (
        SELECT s.id AS story_id,s.title AS story_title,
          COALESCE((SELECT GROUP_CONCAT(c.name,' · ') FROM story_categories sc JOIN categories c ON c.id=sc.category_id WHERE sc.story_id=s.id),c.name,'') AS category_title,
          NULLIF(TRIM(s.image_url),'') AS image_path,1 AS episode_number,
          s.total_episodes AS total_episodes,s.created_at AS published_at
        FROM stories s LEFT JOIN categories c ON c.id=s.category_id
        WHERE s.is_published=1 AND COALESCE(s.audio_url,'')<>''
        UNION ALL
        SELECT s.id AS story_id,s.title AS story_title,
          COALESCE((SELECT GROUP_CONCAT(c.name,' · ') FROM story_categories sc JOIN categories c ON c.id=sc.category_id WHERE sc.story_id=s.id),c.name,'') AS category_title,
          COALESCE(NULLIF(TRIM(sp.image_url),''),NULLIF(TRIM(s.image_url),'')) AS image_path,
          (sp.part_number+1) AS episode_number,s.total_episodes AS total_episodes,sp.created_at AS published_at
        FROM story_parts sp JOIN stories s ON s.id=sp.story_id
        LEFT JOIN categories c ON c.id=s.category_id
        WHERE s.is_published=1 AND sp.is_published=1 AND COALESCE(sp.audio_url,'')<>''
      )
      GROUP BY story_id,episode_number
      ORDER BY published_at DESC,story_id DESC,episode_number DESC LIMIT 30
    `).all();
    return respond(req,{updates:(r.results||[]).map(x=>({...x,story_id:String(x.story_id),episode_number:Number(x.episode_number||1),total_episodes:Number(x.total_episodes||0)}))});
   }

   // Cloudflare D1 shared votes for story characters (one vote per visitor/character).
   if(p==='/api/character-votes'&&req.method==='GET'){
    // Compatible with the existing D1 table: character_votes(story_id, character_id, voter_id, vote, updated_at)
    await env.DB.prepare(`CREATE TABLE IF NOT EXISTS character_votes (
      story_id TEXT NOT NULL,
      character_id TEXT NOT NULL,
      voter_id TEXT NOT NULL,
      vote INTEGER NOT NULL CHECK (vote IN (-1, 1)),
      updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (story_id, character_id, voter_id)
    )`).run();
    const token=String(u.searchParams.get('visitor_id')||'');
    if(!/^[a-zA-Z0-9-]{20,80}$/.test(token))return respond(req,{error:'معرّف الزائر غير صالح'},400);
    const r=await env.DB.prepare(`SELECT story_id,character_id AS character_key,
      SUM(CASE WHEN vote=1 THEN 1 ELSE 0 END) AS likes,
      SUM(CASE WHEN vote=-1 THEN 1 ELSE 0 END) AS dislikes,
      MAX(CASE WHEN voter_id=? THEN vote ELSE 0 END) AS my_vote
      FROM character_votes GROUP BY story_id,character_id`).bind(token).all();
    return respond(req,{votes:(r.results||[]).map(v=>({...v,story_id:String(v.story_id),likes:Number(v.likes||0),dislikes:Number(v.dislikes||0),my_vote:Number(v.my_vote||0)}))});
   }

   if(p==='/api/character-vote'&&req.method==='POST'){
    await env.DB.prepare(`CREATE TABLE IF NOT EXISTS character_votes (
      story_id TEXT NOT NULL,
      character_id TEXT NOT NULL,
      voter_id TEXT NOT NULL,
      vote INTEGER NOT NULL CHECK (vote IN (-1, 1)),
      updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (story_id, character_id, voter_id)
    )`).run();
    const b=await req.json().catch(()=>({}));
    const sid=Number(b.story_id), characterKey=String(b.character_key||''), token=String(b.visitor_id||''), requested=Number(b.vote);
    if(!Number.isInteger(sid)||sid<=0)return respond(req,{error:'القصة غير صالحة'},400);
    if(!['character_one','character_two','character_three'].includes(characterKey))return respond(req,{error:'الشخصية غير صالحة'},400);
    if(!/^[a-zA-Z0-9-]{20,80}$/.test(token))return respond(req,{error:'معرّف الزائر غير صالح'},400);
    if(![-1,0,1].includes(requested))return respond(req,{error:'نوع التصويت غير صالح'},400);
    const story=await env.DB.prepare(`SELECT id,${characterKey}_name AS character_name FROM stories WHERE id=? AND is_published=1`).bind(sid).first();
    if(!story||!String(story.character_name||'').trim())return respond(req,{error:'الشخصية غير موجودة'},404);
    const existing=await env.DB.prepare('SELECT vote FROM character_votes WHERE story_id=? AND character_id=? AND voter_id=?').bind(String(sid),characterKey,token).first();
    let vote=requested;
    if(requested===0||(existing&&Number(existing.vote)===requested)){
      await env.DB.prepare('DELETE FROM character_votes WHERE story_id=? AND character_id=? AND voter_id=?').bind(String(sid),characterKey,token).run();
      vote=0;
    }else{
      await env.DB.prepare(`INSERT INTO character_votes(story_id,character_id,voter_id,vote,updated_at) VALUES(?,?,?,?,CURRENT_TIMESTAMP)
        ON CONFLICT(story_id,character_id,voter_id) DO UPDATE SET vote=excluded.vote,updated_at=CURRENT_TIMESTAMP`).bind(String(sid),characterKey,token,requested).run();
    }
    const counts=await env.DB.prepare(`SELECT SUM(CASE WHEN vote=1 THEN 1 ELSE 0 END) AS likes,SUM(CASE WHEN vote=-1 THEN 1 ELSE 0 END) AS dislikes FROM character_votes WHERE story_id=? AND character_id=?`).bind(String(sid),characterKey).first();
    return respond(req,{success:true,story_id:String(sid),character_key:characterKey,likes:Number(counts?.likes||0),dislikes:Number(counts?.dislikes||0),my_vote:vote});
   }

   // Public comments for anonymous visitors. A persistent visitor token grants
   // ownership of that visitor's own comments without requiring an account.
   if(/^\/api\/story\/\d+\/comments$/.test(p)&&req.method==='GET'){
    const sid=Number(p.split('/')[3]);
    const story=await env.DB.prepare('SELECT id FROM stories WHERE id=? AND is_published=1').bind(sid).first();
    if(!story)return respond(req,{error:'القصة غير موجودة'},404);
    const visitor=String(req.headers.get('X-Visitor-ID')||'');
    // Resolve each comment's current public identity from D1, not from the
    // stale name/photo copied into the comment when it was first posted.
    const r=await env.DB.prepare(`SELECT c.id,c.story_id,c.parent_id,
      COALESCE(NULLIF(p.display_name,''),c.display_name) AS display_name,
      CASE WHEN p.avatar_key IS NOT NULL AND p.avatar_key<>''
        THEN 'https://'||?||'/media/'||p.avatar_key ELSE c.avatar END AS avatar,
      c.content,c.likes_count,c.created_at,c.updated_at,
      CASE WHEN c.visitor_token=? THEN 1 ELSE 0 END AS is_mine,
      EXISTS(SELECT 1 FROM comment_likes l WHERE l.comment_id=c.id AND l.visitor_token=?) AS liked_by_me
      FROM comments c
      LEFT JOIN engagement_profiles p ON p.visitor_id=COALESCE(
        (SELECT account_visitor_id FROM engagement_identity_links WHERE visitor_id=c.visitor_token LIMIT 1),
        c.visitor_token)
      WHERE c.story_id=? ORDER BY c.created_at ASC,c.id ASC LIMIT 1000`)
      .bind(new URL(req.url).host,visitor,visitor,sid).all();
    return respond(req,{comments:(r.results||[]).map(c=>({...c,id:String(c.id),story_id:String(c.story_id),parent_id:c.parent_id==null?null:String(c.parent_id),likes_count:Number(c.likes_count||0),is_mine:Number(c.is_mine)===1,liked_by_me:Number(c.liked_by_me)===1}))});
   }

   if(/^\/api\/story\/\d+\/comments$/.test(p)&&req.method==='POST'){
    const sid=Number(p.split('/')[3]);
    const b=await req.json().catch(()=>({}));
    const token=String(b.visitor_id||'');
    const content=String(b.content||'').trim();
    const submittedName=String(b.display_name||'زائر').trim().slice(0,40)||'زائر';
    await env.DB.prepare(`CREATE TABLE IF NOT EXISTS engagement_profiles(
      visitor_id TEXT PRIMARY KEY,display_name TEXT NOT NULL DEFAULT 'مستخدم Dramio',
      avatar_key TEXT NOT NULL DEFAULT '',xp INTEGER NOT NULL DEFAULT 0,
      is_member INTEGER NOT NULL DEFAULT 0,last_seen TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    )`).run();
    await env.DB.prepare(`CREATE TABLE IF NOT EXISTS engagement_identity_links(
      visitor_id TEXT PRIMARY KEY,account_visitor_id TEXT NOT NULL,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    )`).run();
    const profile=await env.DB.prepare(`SELECT display_name,avatar_key FROM engagement_profiles WHERE visitor_id=COALESCE(
      (SELECT account_visitor_id FROM engagement_identity_links WHERE visitor_id=? LIMIT 1),?)`).bind(token,token).first();
    const displayName=String(profile?.display_name||submittedName).trim().slice(0,40)||'زائر';
    const avatar=profile?.avatar_key ? `https://${new URL(req.url).host}/media/${profile.avatar_key}` : String(b.avatar||'dramio-default').slice(0,500);
    const parentId=b.parent_id==null||b.parent_id===''?null:Number(b.parent_id);
    if(!/^[a-zA-Z0-9-]{20,80}$/.test(token))return respond(req,{error:'تعذر التحقق من هوية الزائر. حدّث الصفحة وحاول مجددًا.'},400);
    if(content.length<1||content.length>1000)return respond(req,{error:'اكتب تعليقًا من 1 إلى 1000 حرف.'},400);
    const story=await env.DB.prepare('SELECT id FROM stories WHERE id=? AND is_published=1').bind(sid).first();
    if(!story)return respond(req,{error:'القصة غير موجودة'},404);
    if(parentId!==null){
     const parent=await env.DB.prepare('SELECT id FROM comments WHERE id=? AND story_id=?').bind(parentId,sid).first();
     if(!parent)return respond(req,{error:'التعليق المراد الرد عليه غير موجود'},404);
    }
    const r=await env.DB.prepare('INSERT INTO comments(story_id,parent_id,visitor_token,display_name,avatar,content) VALUES(?,?,?,?,?,?)')
     .bind(sid,parentId,token,displayName,avatar,content).run();
    if(parentId!==null){
     try{
      const storyTitle=await env.DB.prepare('SELECT title FROM stories WHERE id=?').bind(sid).first();
      await notifyCommentReply(env,parentId,sid,String(storyTitle?.title||'Dramio'),content,displayName);
     }catch(error){console.error('Comment reply notification delivery failed',error);}
    }
    return respond(req,{success:true,id:String(r.meta?.last_row_id||''),parent_id:parentId===null?null:String(parentId),display_name:displayName,avatar,content,likes_count:0,created_at:new Date().toISOString() },201);
   }

   if(/^\/api\/comment\/\d+$/.test(p)&&['PUT','DELETE'].includes(req.method)){
    const cid=Number(p.split('/')[3]);
    const b=await req.json().catch(()=>({}));
    const token=String(b.visitor_id||'');
    if(!/^[a-zA-Z0-9-]{20,80}$/.test(token))return respond(req,{error:'تعذر التحقق من ملكية التعليق'},400);
    const existing=await env.DB.prepare('SELECT id FROM comments WHERE id=? AND visitor_token=?').bind(cid,token).first();
    if(!existing)return respond(req,{error:'يمكنك تعديل أو حذف تعليقاتك فقط'},403);
    if(req.method==='DELETE'){
     await env.DB.prepare('DELETE FROM comments WHERE id=?').bind(cid).run();
     return respond(req,{success:true});
    }
    const content=String(b.content||'').trim();
    if(content.length<1||content.length>1000)return respond(req,{error:'اكتب تعليقًا من 1 إلى 1000 حرف.'},400);
    await env.DB.prepare('UPDATE comments SET content=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND visitor_token=?').bind(content,cid,token).run();
    return respond(req,{success:true,content});
   }

   if(/^\/api\/comment\/\d+\/like$/.test(p)&&req.method==='POST'){
    const cid=Number(p.split('/')[3]);
    const b=await req.json().catch(()=>({}));
    const token=String(b.visitor_id||'');
    if(!/^[a-zA-Z0-9-]{20,80}$/.test(token))return respond(req,{error:'تعذر التحقق من هوية الزائر'},400);
    const comment=await env.DB.prepare('SELECT id FROM comments WHERE id=?').bind(cid).first();
    if(!comment)return respond(req,{error:'التعليق غير موجود'},404);
    const liked=await env.DB.prepare('SELECT comment_id FROM comment_likes WHERE comment_id=? AND visitor_token=?').bind(cid,token).first();
    if(liked)await env.DB.prepare('DELETE FROM comment_likes WHERE comment_id=? AND visitor_token=?').bind(cid,token).run();
    else await env.DB.prepare('INSERT OR IGNORE INTO comment_likes(comment_id,visitor_token) VALUES(?,?)').bind(cid,token).run();
    const count=await env.DB.prepare('SELECT COUNT(*) AS n FROM comment_likes WHERE comment_id=?').bind(cid).first();
    await env.DB.prepare('UPDATE comments SET likes_count=? WHERE id=?').bind(Number(count?.n||0),cid).run();
    return respond(req,{success:true,liked:!liked,likes_count:Number(count?.n||0)});
   }

   if(p==='/api/admin/comments'&&req.method==='GET'){
    if(!(await adminOk(req,env)))return respond(req,{error:'Unauthorized'},401);
    const r=await env.DB.prepare(`SELECT c.id,c.story_id,c.parent_id,c.display_name,c.avatar,c.content,c.likes_count,c.created_at,s.title AS story_title
      FROM comments c LEFT JOIN stories s ON s.id=c.story_id ORDER BY c.created_at DESC LIMIT 1000`).all();
    return respond(req,{comments:(r.results||[]).map(c=>({...c,id:String(c.id),story_id:String(c.story_id),parent_id:c.parent_id==null?null:String(c.parent_id),likes_count:Number(c.likes_count||0)}))});
   }

   if(/^\/api\/admin\/comment\/\d+$/.test(p)&&req.method==='DELETE'){
    if(!(await adminOk(req,env)))return respond(req,{error:'Unauthorized'},401);
    const cid=Number(p.split('/')[4]);
    await env.DB.prepare('DELETE FROM comments WHERE id=?').bind(cid).run();
    return respond(req,{success:true});
   }

   if(/^\/api\/story\/\d+\/parts$/.test(p)&&req.method==='GET'){
    const sid=Number(p.split('/')[3]);

    const story=await env.DB.prepare(
     'SELECT id FROM stories WHERE id=? AND is_published=1'
    ).bind(sid).first();

    if(!story){
     return respond(req,{error:'القصة غير موجودة'},404);
    }

    const r=await env.DB.prepare(
     'SELECT id,story_id,title,audio_url AS audio_path,image_url AS image_path,part_number AS sort_order,created_at AS published_at FROM story_parts WHERE story_id=? AND is_published=1 ORDER BY part_number,id'
    ).bind(sid).all();

    return respond(req,{
     parts:(r.results||[]).map(x=>({
      ...x,
      id:String(x.id),
      story_id:String(x.story_id)
     }))
    });
   }

   if(/^\/api\/admin\/story\/\d+\/parts$/.test(p)&&req.method==='GET'){
    if(!(await adminOk(req,env))){
     return respond(req,{error:'Unauthorized'},401);
    }

    const sid=Number(p.split('/')[4]);

    const r=await env.DB.prepare(
     'SELECT id,story_id,title,audio_url AS audio_path,image_url AS image_path,part_number AS sort_order FROM story_parts WHERE story_id=? ORDER BY part_number,id'
    ).bind(sid).all();

    return respond(req,{
     parts:(r.results||[]).map(x=>({
      ...x,
      id:String(x.id),
      story_id:String(x.story_id)
     }))
    });
   }

   if(/^\/api\/admin\/story\/\d+\/parts$/.test(p)&&req.method==='POST'){
    if(!(await adminOk(req,env))){
     return respond(req,{error:'Unauthorized'},401);
    }

    const sid=Number(p.split('/')[4]);
    const b=await req.json();

    if(
     !Number.isInteger(sid)||
     !String(b.title||'').trim()||
     !String(b.audio_path||'').startsWith('audio/')
    ){
     return respond(req,{
      error:'عنوان الجزء وملف MP3 مطلوبان'
     },400);
    }

    const story=await env.DB.prepare(
     'SELECT id FROM stories WHERE id=?'
    ).bind(sid).first();

    if(!story){
     return respond(req,{error:'القصة غير موجودة'},404);
    }

    const max=await env.DB.prepare(
     'SELECT COALESCE(MAX(part_number),0) AS n FROM story_parts WHERE story_id=?'
    ).bind(sid).first();

    const r=await env.DB.prepare(
     'INSERT INTO story_parts(story_id,part_number,title,audio_url,image_url) VALUES(?,?,?,?,?)'
    ).bind(
     sid,
     Number(max?.n||0)+1,
     String(b.title).trim().slice(0,180),
     b.audio_path,
     b.image_path||null
    ).run();

    // Notify only users who are still subscribed to this story.
    try{
     const storyTitle=await env.DB.prepare('SELECT title FROM stories WHERE id=?').bind(sid).first();
     await notifyStorySubscribers(env,sid,String(storyTitle?.title||'Dramio'),String(b.title).trim());
    }catch(error){console.error('Story notification delivery failed',error);}
    return respond(req,{
     success:true,
     id:String(r.meta?.last_row_id||'')
    });
   }

   if(/^\/api\/admin\/part\/\d+$/.test(p)&&['PUT','DELETE'].includes(req.method)){
    if(!(await adminOk(req,env))){
     return respond(req,{error:'Unauthorized'},401);
    }

    const pid=Number(p.split('/')[4]);

    const old=await env.DB.prepare(
     'SELECT * FROM story_parts WHERE id=?'
    ).bind(pid).first();

    if(!old){
     return respond(req,{error:'الجزء غير موجود'},404);
    }

    if(req.method==='DELETE'){
     await env.DB.prepare(
      'DELETE FROM story_parts WHERE id=?'
     ).bind(pid).run();

     for(const key of [old.audio_url,old.image_url]){
      if(
       key&&
       /^(audio\/[0-9a-f-]+\.mp3|images\/[0-9a-f-]+\.(jpg|png|webp))$/i.test(key)
      ){
       await env.MEDIA.delete(key);
      }
     }

     return respond(req,{success:true});
    }

    const b=await req.json();

    if(!String(b.title||'').trim()){
     return respond(req,{error:'عنوان الجزء مطلوب'},400);
    }

    await env.DB.prepare(
     'UPDATE story_parts SET title=?,audio_url=?,image_url=?,updated_at=CURRENT_TIMESTAMP WHERE id=?'
    ).bind(
     String(b.title).trim().slice(0,180),
     b.audio_path||old.audio_url,
     b.image_path||old.image_url||null,
     pid
    ).run();

    if(
     b.audio_path&&
     b.audio_path!==old.audio_url&&
     /^audio\/[0-9a-f-]+\.mp3$/i.test(old.audio_url)
    ){
     await env.MEDIA.delete(old.audio_url);
    }

    return respond(req,{success:true});
   }

   if(p.startsWith('/media/')&&['GET','HEAD'].includes(req.method)){
    const key=decodeURIComponent(p.slice(7));

    if(
     !/^(audio|images)\/[a-zA-Z0-9._/-]+$/.test(key)||
     key.split('/').includes('..')
    ){
     return respond(req,{error:'Invalid media path'},400);
    }

    const rangeHeader=req.headers.get('Range');
    let obj,status=200,start=0,end=-1;

    if(rangeHeader){
     const base=await env.MEDIA.head(key);

     if(!base){
      return respond(req,{error:'Not found'},404);
     }

     const size=base.size;
     const match=/^bytes=(\d*)-(\d*)$/.exec(rangeHeader.trim());

     if(!match||(!match[1]&&!match[2])){
      return new Response(null,{
       status:416,
       headers:{
        'Content-Range':`bytes */${size}`,
        'Accept-Ranges':'bytes',
        ...corsHeaders(origin)
       }
      });
     }

     if(!match[1]){
      const suffix=Number(match[2]);
      start=Math.max(0,size-suffix);
      end=size-1;
     }else{
      start=Number(match[1]);
      end=match[2]?Math.min(Number(match[2]),size-1):size-1;
     }

     if(
      !Number.isSafeInteger(start)||
      !Number.isSafeInteger(end)||
      start<0||
      start>=size||
      end<start
     ){
      return new Response(null,{
       status:416,
       headers:{
        'Content-Range':`bytes */${size}`,
        'Accept-Ranges':'bytes',
        ...corsHeaders(origin)
       }
      });
     }

     obj=await env.MEDIA.get(key,{
      range:{offset:start,length:end-start+1}
     });

     status=206;
    }else{
     obj=await env.MEDIA.get(key);

     if(!obj){
      return respond(req,{error:'Not found'},404);
     }

     end=obj.size-1;
    }

    if(!obj){
     return respond(req,{error:'Not found'},404);
    }

    const h=new Headers(corsHeaders(origin));

    h.set(
     'Content-Type',
     obj.httpMetadata?.contentType||'application/octet-stream'
    );

    h.set('Cache-Control','public, max-age=3600');
    h.set('Accept-Ranges','bytes');

    if(status===206){
     h.set(
      'Content-Range',
      `bytes ${start}-${end}/${(await env.MEDIA.head(key)).size}`
     );

     h.set('Content-Length',String(end-start+1));
    }else if(obj.size){
     h.set('Content-Length',String(obj.size));
    }

    return new Response(req.method==='HEAD'?null:obj.body,{
     status,
     headers:h
    });
   }

   if(p==='/api/admin/login'&&req.method==='POST'){
    return respond(req,{error:'استخدمي تسجيل الدخول بحساب Google الموثّق للمشرف.'},410);
   }

   if(p==='/api/upload-url'&&req.method==='POST'){
    if(!(await adminOk(req,env))){
     return respond(req,{error:'Unauthorized'},401);
    }

    const kind=u.searchParams.get('kind');
    const type=u.searchParams.get('type')||'';
    const filename=safeFile(u.searchParams.get('filename'));

    let folder,ext;

    if(
     kind==='audio'&&
     /\.mp3$/i.test(filename)&&
     (
      type==='audio/mpeg'||
      type==='audio/mp3'||
      type==='application/octet-stream'
     )
    ){
     folder='audio';
     ext='.mp3';
    }else if(
     ['story','story-wide','category','part-image','story-character'].includes(kind)&&
     ['image/jpeg','image/png','image/webp'].includes(type)
    ){
     folder='images';
     ext=type==='image/jpeg'?'.jpg':
         type==='image/png'?'.png':'.webp';
    }else{
     return respond(req,{
      error:'نوع الملف غير مدعوم. استخدمي MP3 وصور JPG أو PNG أو WebP.'
     },415);
    }

    return respond(req,{key:folder+'/'+id()+ext});
   }

   if(p.startsWith('/api/upload/')&&req.method==='PUT'){
    if(!(await adminOk(req,env))){
     return respond(req,{error:'Unauthorized'},401);
    }

    const key=decodeURIComponent(
     p.slice('/api/upload/'.length)
    );

    if(
     !/^(audio|images)\/[0-9a-f-]{36}\.(mp3|jpg|png|webp)$/i.test(key)
    ){
     return respond(req,{error:'Invalid upload key'},400);
    }

    const type=req.headers.get('Content-Type')||'application/octet-stream';
    const isAudio=key.startsWith('audio/');

    if(
     isAudio&&
     type!=='audio/mpeg'&&
     type!=='audio/mp3'&&
     type!=='application/octet-stream'
    ){
     return respond(req,{error:'ملف الصوت يجب أن يكون MP3'},415);
    }

    const max=isAudio?100*1024*1024:8*1024*1024;
    const buf=await req.arrayBuffer();

    if(buf.byteLength>max){
     return respond(req,{
      error:isAudio
       ?'حجم MP3 يتجاوز 100 ميغابايت'
       :'حجم الصورة يتجاوز 8 ميغابايت'
     },413);
    }

    await env.MEDIA.put(key,buf,{
     httpMetadata:{
      contentType:isAudio?'audio/mpeg':type
     }
    });

    return respond(req,{success:true,key});
   }

   if(p==='/api/admin/catalog'&&req.method==='GET'){
    if(!(await adminOk(req,env))){
     return respond(req,{error:'Unauthorized'},401);
    }
    await ensureCategories(env);

    const [cats,stories]=await Promise.all([
     env.DB.prepare(
      'SELECT id,name AS title,slug,NULL AS emoji,sort_order,image_url AS image_path FROM categories WHERE is_active=1 ORDER BY sort_order,id'
     ).all(),

     env.DB.prepare(
      'SELECT id,title,description,category_id,audio_url AS audio_path,image_url AS image_path,wide_image_url AS wide_image_path,suggested_image_url AS suggested_image_path,total_episodes,status,character_one_name,character_two_name,character_three_name,character_one_image_url AS character_one_image_path,character_two_image_url AS character_two_image_path,character_three_image_url AS character_three_image_path,views,created_at FROM stories ORDER BY created_at DESC,id DESC'
     ).all()
    ]);

    const enriched=await attachStoryCategories(env,stories.results||[]);
    return respond(req,{
     categories:(cats.results||[]).map(c=>({...c,id:String(c.id),emoji:CATEGORIES.find(x=>x.slug===c.slug)?.emoji||'📚'})),
     stories:enriched.map(s=>({...s,id:String(s.id),category_id:String(s.category_id),category_ids:s.category_ids.map(String)}))
    });
   }

   if(p==='/api/admin/category'&&req.method==='POST'){
    if(!(await adminOk(req,env))){
     return respond(req,{error:'Unauthorized'},401);
    }

    const b=await req.json();
    const title=String(b.title||'').trim();

    if(!title||!b.image_path){
     return respond(req,{
      error:'اسم التصنيف وصورة الغلاف مطلوبان'
     },400);
    }

    const max=await env.DB.prepare(
     'SELECT COALESCE(MAX(sort_order),0) AS n FROM categories'
    ).first();

    const slug='custom-'+crypto.randomUUID();

    const result=await env.DB.prepare(
     'INSERT INTO categories (name,slug,image_url,sort_order,is_active) VALUES (?,?,?,?,1)'
    ).bind(
     title.slice(0,100),
     slug,
     b.image_path,
     Number(max?.n||0)+1
    ).run();

    return respond(req,{
     success:true,
     id:result.meta?.last_row_id
    });
   }

   if(p.startsWith('/api/admin/category/')&&req.method==='PUT'){
    if(!(await adminOk(req,env))){
     return respond(req,{error:'Unauthorized'},401);
    }

    const cid=decodeURIComponent(p.split('/').pop());
    const b=await req.json();

    if(!/^\d+$/.test(cid)){
     return respond(req,{error:'التصنيف غير موجود'},404);
    }

    if(!String(b.title||'').trim()){
     return respond(req,{error:'اسم التصنيف مطلوب'},400);
    }

    const old=await env.DB.prepare(
     'SELECT image_url FROM categories WHERE id=?'
    ).bind(Number(cid)).first();

    if(!old){
     return respond(req,{error:'التصنيف غير موجود'},404);
    }

    await env.DB.prepare(
     'UPDATE categories SET name=?,image_url=COALESCE(?,image_url) WHERE id=?'
    ).bind(
     String(b.title).trim().slice(0,100),
     b.image_path||null,
     Number(cid)
    ).run();

    if(
     b.image_path&&
     old.image_url&&
     old.image_url!==b.image_path&&
     /^images\/[0-9a-f-]{36}\.(jpg|png|webp)$/i.test(old.image_url)
    ){
     await env.MEDIA.delete(old.image_url);
    }

    return respond(req,{success:true});
   }

   if(p.startsWith('/api/admin/category/')&&req.method==='DELETE'){
    if(!(await adminOk(req,env))){
     return respond(req,{error:'Unauthorized'},401);
    }

    const cid=decodeURIComponent(p.split('/').pop());

    if(!/^\d+$/.test(cid)){
     return respond(req,{error:'التصنيف غير موجود'},404);
    }

    const old=await env.DB.prepare(
     'SELECT id,image_url FROM categories WHERE id=?'
    ).bind(Number(cid)).first();

    if(!old){
     return respond(req,{error:'التصنيف غير موجود'},404);
    }

    const used=await env.DB.prepare(
     'SELECT COUNT(*) AS n FROM stories WHERE category_id=?'
    ).bind(Number(cid)).first();

    if(Number(used?.n||0)>0){
     return respond(req,{
      error:'لا يمكن حذف تصنيف يحتوي على قصص. انقلي القصص إلى تصنيف آخر أو احذفيها أولاً.'
     },409);
    }

    await env.DB.prepare(
     'UPDATE categories SET is_active=0 WHERE id=?'
    ).bind(Number(cid)).run();

    if(
     old.image_url&&
     /^images\/[0-9a-f-]{36}\.(jpg|png|webp)$/i.test(old.image_url)
    ){
     await env.MEDIA.delete(old.image_url);
    }

    return respond(req,{success:true});
   }

   if(p==='/api/admin/story'&&req.method==='POST'){
    if(!(await adminOk(req,env)))return respond(req,{error:'Unauthorized'},401);
    await ensureCategories(env);
    const b=await req.json();
    if(!String(b.title||'').trim()||!b.audio_path||!b.image_path)return respond(req,{error:'العنوان وملف MP3 والصورة مطلوبة'},400);
    let categoryIds;
    try{categoryIds=await categoryIdsFromBody(env,b);}catch(e){return respond(req,{error:e.message},400);}
    const result=await env.DB.prepare('INSERT INTO stories (title,description,category_id,audio_url,image_url,wide_image_url,suggested_image_url,total_episodes,status,character_one_name,character_two_name,character_three_name,character_one_image_url,character_two_image_url,character_three_image_url,views,is_published) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,1)').bind(
     String(b.title).trim().slice(0,180),String(b.description||'').slice(0,5000),categoryIds[0],b.audio_path,b.image_path,b.wide_image_path||null,b.suggested_image_path||null,Math.max(1,Math.min(999,Math.floor(Number(b.total_episodes)||1))),b.status==='completed'?'completed':'ongoing',String(b.character_one_name||'البطلة').slice(0,100),String(b.character_two_name||'البطل').slice(0,100),String(b.character_three_name||'شخصية محورية').slice(0,100),b.character_one_image_path||null,b.character_two_image_path||null,b.character_three_image_path||null
    ).run();
    const storyId=Number(result.meta?.last_row_id);
    await saveStoryCategories(env,storyId,categoryIds);
    return respond(req,{success:true,id:storyId});
   }

   if(
    p.startsWith('/api/admin/story/')&&
    ['PUT','DELETE'].includes(req.method)
   ){
    if(!(await adminOk(req,env))){
     return respond(req,{error:'Unauthorized'},401);
    }

    const sid=decodeURIComponent(p.split('/').pop());

    if(!/^\d+$/.test(sid)){
     return respond(req,{error:'القصة غير موجودة'},404);
    }

    const old=await env.DB.prepare(
     'SELECT * FROM stories WHERE id=?'
    ).bind(Number(sid)).first();

    if(!old){
     return respond(req,{error:'القصة غير موجودة'},404);
    }

    if(req.method==='DELETE'){
     const parts=await env.DB.prepare(
      'SELECT audio_url,image_url FROM story_parts WHERE story_id=?'
     ).bind(Number(sid)).all();

     for(const part of (parts.results||[])){
      for(const key of [part.audio_url,part.image_url]){
       if(
        key&&
        /^(audio\/[0-9a-f-]+\.mp3|images\/[0-9a-f-]+\.(jpg|png|webp))$/i.test(key)
       ){
        await env.MEDIA.delete(key);
       }
      }
     }

     await env.DB.prepare(
      'DELETE FROM story_parts WHERE story_id=?'
     ).bind(Number(sid)).run();

     await env.DB.prepare(
      'DELETE FROM stories WHERE id=?'
     ).bind(Number(sid)).run();

     for(const key of [
      old.audio_url,
      old.image_url,
      old.wide_image_url,
      old.suggested_image_url
     ]){
      if(
       key&&
       /^(audio|images)\/[0-9a-f-]+\.(mp3|jpg|png|webp)$/i.test(key)
      ){
       await env.MEDIA.delete(key);
      }
     }

     return respond(req,{success:true});
    }

    await ensureCategories(env);
    const b=await req.json();
    const title=String(b.title||old.title).trim();
    let categoryIds;
    try{categoryIds=await categoryIdsFromBody(env,(b.category_ids||b.category_id)?b:{...b,category_id:old.category_id});}
    catch(e){return respond(req,{error:e.message},400);}
    const category=categoryIds[0];
    if(!title)return respond(req,{error:'العنوان مطلوب'},400);

    const requestedTotal = Number(b.total_episodes);
    if (!Number.isInteger(requestedTotal) || requestedTotal < 1 || requestedTotal > 999) {
     return respond(req,{error:'عدد الحلقات يجب أن يكون عددًا صحيحًا من 1 إلى 999'},400);
    }

    await env.DB.prepare(
     'UPDATE stories SET title=?,description=?,category_id=?,audio_url=?,image_url=?,wide_image_url=?,suggested_image_url=?,total_episodes=?,status=?,character_one_name=?,character_two_name=?,character_three_name=?,character_one_image_url=?,character_two_image_url=?,character_three_image_url=?,updated_at=CURRENT_TIMESTAMP WHERE id=?'
    ).bind(
     title.slice(0,180),
     String(b.description??old.description??'').slice(0,5000),
     Number(category),
     b.audio_path||old.audio_url,
     b.image_path||old.image_url,
     b.wide_image_path||old.wide_image_url||null,
     b.suggested_image_path||old.suggested_image_url||null,
     requestedTotal,
     b.status==='completed'?'completed':(b.status==='ongoing'?'ongoing':(old.status||'ongoing')),
     String(b.character_one_name??old.character_one_name??'البطلة').slice(0,100),
     String(b.character_two_name??old.character_two_name??'البطل').slice(0,100),
     String(b.character_three_name??old.character_three_name??'شخصية محورية').slice(0,100),
     b.character_one_image_path||old.character_one_image_url||null,
     b.character_two_image_path||old.character_two_image_url||null,
     b.character_three_image_path||old.character_three_image_url||null,
     Number(sid)
    ).run();
    await saveStoryCategories(env,Number(sid),categoryIds);

    for(const key of [
     b.audio_path&&b.audio_path!==old.audio_url?old.audio_url:null,
     b.image_path&&b.image_path!==old.image_url?old.image_url:null,
     b.wide_image_path&&b.wide_image_path!==old.wide_image_url?old.wide_image_url:null,
     b.suggested_image_path&&b.suggested_image_path!==old.suggested_image_url?old.suggested_image_url:null,
      b.character_one_image_path&&b.character_one_image_path!==old.character_one_image_url?old.character_one_image_url:null,
      b.character_two_image_path&&b.character_two_image_path!==old.character_two_image_url?old.character_two_image_url:null,
      b.character_three_image_path&&b.character_three_image_path!==old.character_three_image_url?old.character_three_image_url:null
    ]){
     if(key)await env.MEDIA.delete(key);
    }

    return respond(req,{success:true});
   }

   if(p.startsWith('/api/view/')&&req.method==='POST'){
    const sid=decodeURIComponent(p.split('/').pop());

    if(!/^\d+$/.test(sid)){
     return respond(req,{error:'القصة غير موجودة'},404);
    }

    const r=await env.DB.prepare(
     'UPDATE stories SET views=views+1 WHERE id=? AND is_published=1 RETURNING views'
    ).bind(Number(sid)).first();

    if(!r){
     return respond(req,{error:'القصة غير موجودة'},404);
    }

    return respond(req,{success:true,views:r.views});
   }

   return respond(req,{error:'Not found'},404);

  }catch(e){
   return respond(req,{error:e?.message||'Server error'},500);
  }
 }
};
