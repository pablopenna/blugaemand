const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const source=fs.readFileSync(process.argv[2] || 'DarwinBridge/JIT/darwinbridge-universal.js','utf8');
function h(n) {return BigInt(n).toString(16).padStart(16,'0').match(/../g).reverse().join('');}
function stop(pc=0x1000,cmd=1,sig='05') {return `T${sig}thread:a;20:${h(pc)};00:${h(0x2000)};01:${h(4096)};10:${h(cmd)};`;}
function run(stops,insn,extra={}) {
 const sent=[],logs=[];let prep=0;
 vm.runInNewContext(source,{get_pid:()=>123,log:s=>logs.push(s),prepare_memory_region:()=>{prep++;return true;},send_command:q=>{
  sent.push(q);
  if(q.startsWith('vAttach'))return extra.attach || stop();
  if(q.startsWith('vCont'))return stops.shift() || 'W00';
  if(q.startsWith('m'))return insn;
  if(q.startsWith('P')||q==='D')return 'OK';
  if(extra.reply)return extra.reply(q);
  return '';
 },Date,Map,Set,BigInt,JSON},{timeout:1000});return {sent,logs,prep};
}
let a=run([stop(), 'W00'],'a0013ed4'); // BRK #0xf00d
assert.equal(a.prep,1);assert(a.sent.includes('P20='+h(0x1004)+';thread:a;'));
assert(a.sent.includes('vCont;c'));assert(!a.sent.some(s=>s.startsWith('vCont;S')));
a=run([stop(), 'X05'],'000020d4'); // unowned BRK #0
assert(!a.sent.some(s=>s.startsWith('P20=')));assert(a.sent.includes('vCont;C05:a;c'));
a=run([stop(0x1000,1,'0b'),'X0b'],'1f2003d5');
assert(a.sent.includes('vCont;C0b:a;c'));assert(a.logs.some(s=>s.includes('pc-memory')));
a=run([],null,{attach:'E01'});assert(!a.sent.some(s=>s.startsWith('vCont')));
a=run(['T05thread:a;','W00'],'a0013ed4',{reply:q=> q.startsWith('p20')?h(0x1000):q.startsWith('p10')?h(1):q.startsWith('p0;')?h(0x2000):q.startsWith('p1;')?h(4096):''});assert.equal(a.prep,1);
a=run([stop(0x1000,99),'X05'],'a0013ed4');assert(!a.sent.some(s=>s.startsWith('P20=')));assert(a.sent.includes('vCont;C05:a;c'));
a=run(['E22'],null);assert(a.sent.includes('D'));assert(a.logs.some(s=>s.includes('diagnostic-error')));
console.log('PASS: attach, JIT prepare, sparse stops, unknown BRK, fatal signal, unsupported command, resume error');
// Genuine non-SIGTRAP at an instruction matching the JIT opcode must not be eaten.
a=run([stop(0x1000,1,'0b'),'X0b'],'a0013ed4');assert.equal(a.prep,0);assert(!a.sent.some(s=>s.startsWith('P20=')));assert(a.sent.includes('vCont;C0b:a;c'));
// External breakpoint observation uses in-memory Mach-O headers, not a guessed PC.
{
 const memory=new Map(),sent=[],logs=[];
 const header=Buffer.alloc(32);header.writeUInt32LE(0xfeedfacf);header.writeUInt32LE(2,16);header.writeUInt32LE(176,20);
 const cmds=Buffer.alloc(176);cmds.writeUInt32LE(0x19);cmds.writeUInt32LE(152,4);cmds.write('__TEXT',8);cmds.writeUInt32LE(1,64);
 cmds.write('__init_offsets',72);cmds.write('__TEXT',88);cmds.writeBigUInt64LE(0x200n,104);cmds.writeBigUInt64LE(4n,112);cmds.writeUInt32LE(0x16,136);
 cmds.writeUInt32LE(0x80000028,152);cmds.writeUInt32LE(24,156);cmds.writeBigUInt64LE(0x400n,160);
 memory.set('m5000,20',header.toString('hex'));memory.set('m5020,b0',cmds.toString('hex'));memory.set('m5200,4','00030000');
 const stops=[stop(0x5300),stop(0x5400),'W00'];
 vm.runInNewContext(source,{get_pid:()=>123,log:s=>logs.push(s),prepare_memory_region:()=>true,send_command:q=>{
  sent.push(q);if(q.startsWith('vAttach'))return stop();
  if(q.startsWith('vCont'))return stops.shift();
  if(q.startsWith('jGetLoaded'))return JSON.stringify({images:[{load_address:0x5000,pathname:'/guest/DBBootstrap.dylib',uuid:'test'}]});
  if(q.startsWith('Z1')||q.startsWith('z1')||q==='D')return 'OK';
  return memory.get(q)||'';
 },Date,Map,Set,BigInt,JSON},{timeout:1000});
 assert(logs.some(s=>s.includes('boundary DBBootstrap.dylib:initializer:0')));
 assert(logs.some(s=>s.includes('boundary DBBootstrap.dylib:LC_MAIN')));
 assert(sent.includes('z1,5300,4'));assert(sent.includes('z1,5400,4'));
 assert(!sent.some(s=>s.startsWith('P20='))); // hardware stops resume the original instruction
 assert.equal(sent.filter(s=>s==='Z1,5300,4').length,1);
}
console.log('PASS: exception at JIT opcode, live-image initializer/entry breakpoint dispatch');
