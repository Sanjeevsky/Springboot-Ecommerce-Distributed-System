#!/usr/bin/env python3
"""
Generates architecture.html by scanning the live project.

Reads:
  - docker-compose.yml          → service ports, infra deps (Kafka/Redis/MySQL)
  - application*.properties     → spring.application.name, server.port
  - src/**/*.java               → @FeignClient, @KafkaListener, kafkaTemplate.send

Run any time you add a service, change a port, or add a Feign/Kafka connection:
    python3 generate-arch.py
"""

import os, re, json
from pathlib import Path

ROOT = Path(__file__).parent

# ── 1. DISCOVER SERVICES ─────────────────────────────────────────────────────

INFRA_DIRS = {'service-discovery', 'cloud-config', 'spring-server', 'platform-commons'}
INFRA_NAMES = {'mysql', 'redis', 'kafka', 'zookeeper'}

def find_service_dirs():
    """Every immediate subdirectory with a pom.xml, minus infra and platform-commons."""
    dirs = []
    for d in sorted(ROOT.iterdir()):
        if d.is_dir() and (d / 'pom.xml').exists() and d.name not in {'platform-commons'}:
            dirs.append(d)
    return dirs

# ── 2. READ APPLICATION PROPERTIES ───────────────────────────────────────────

def read_props(service_dir):
    """Return merged dict from all application*.properties and application*.yml in resources."""
    props = {}
    for f in service_dir.rglob('application*.properties'):
        if 'test' in str(f): continue
        for line in f.read_text(errors='ignore').splitlines():
            line = line.strip()
            if '=' in line and not line.startswith('#'):
                k, _, v = line.partition('=')
                props[k.strip()] = v.strip()
    # Also parse YAML (covers api-gateway which uses application.yml)
    for f in service_dir.rglob('application*.yml'):
        if 'test' in str(f): continue
        src = f.read_text(errors='ignore')
        # spring.application.name
        m = re.search(r'name:\s*([^\s#\n]+)', src)
        if m and 'spring.application.name' not in props:
            props['spring.application.name'] = m.group(1).strip()
        # server.port — find "port:" that appears under a "server:" section
        m = re.search(r'server\s*:\s*\n(?:\s+\w[^\n]*\n)*?\s+port\s*:\s*(\d+)', src)
        if m and 'server.port' not in props:
            props['server.port'] = m.group(1)
        # Also catch top-level "  port: NNNN" pattern (2-space indent typical in Spring YAML)
        if 'server.port' not in props:
            m = re.search(r'^\s{2}port\s*:\s*(\d+)', src, re.MULTILINE)
            if m:
                props['server.port'] = m.group(1)
    return props

# ── 3. SCAN JAVA FILES ────────────────────────────────────────────────────────

def scan_java(service_dir):
    """
    Returns:
      feign_clients: list of lb:// service-names this service calls
      kafka_pub:     set of topic names this service publishes to
      kafka_sub:     set of topic names this service subscribes to
    """
    feign_clients = []
    kafka_pub = set()
    kafka_sub = set()

    # Phase 1: collect all topic-constant definitions across the service
    #   e.g. public static final String ORDER_EVENTS_TOPIC = "order-events";
    topic_constants = {}  # CONST_NAME → "topic-string-value"
    has_kafka_send_files = set()  # file paths that call kafkaTemplate.send

    all_java = [f for f in service_dir.rglob('*.java') if 'test' not in str(f).lower()]

    for f in all_java:
        src = f.read_text(errors='ignore')
        for m in re.finditer(r'static final String (\w+)\s*=\s*"([^"]+)"', src):
            val = m.group(2)
            if 'event' in val or 'topic' in val:
                topic_constants[m.group(1)] = val
        if 'kafkaTemplate.send' in src:
            has_kafka_send_files.add(str(f))

    # Phase 2: per-file scanning
    for f in all_java:
        src = f.read_text(errors='ignore')

        # @FeignClient(name = "...", value = "...", ...)
        for m in re.finditer(r'@FeignClient\s*\([^)]*(?:name|value)\s*=\s*"([^"]+)"', src):
            feign_clients.append(m.group(1).lower())

        # kafkaTemplate.send("literal-topic", ...)
        for m in re.finditer(r'kafkaTemplate\.send\s*\(\s*"([^"]+)"', src):
            kafka_pub.add(m.group(1))

        # kafkaTemplate.send(CONST_NAME, ...) — resolve via collected constants
        for m in re.finditer(r'kafkaTemplate\.send\s*\(\s*([A-Z_][A-Z_0-9]+)\s*,', src):
            const_name = m.group(1)
            if const_name in topic_constants:
                kafka_pub.add(topic_constants[const_name])

        # kafkaTemplate.send(ClassName.CONST_NAME, ...) — qualified constant reference
        for m in re.finditer(r'kafkaTemplate\.send\s*\(\s*\w+\.([A-Z_][A-Z_0-9]+)\s*,', src):
            const_name = m.group(1)
            if const_name in topic_constants:
                kafka_pub.add(topic_constants[const_name])

        # @KafkaListener(topics = "..." or topics = {"a","b"})
        for m in re.finditer(r'@KafkaListener\s*\(([^)]+)\)', src):
            annotation = m.group(1)
            # Extract topics value (stops at next key= or end of annotation)
            tm = re.search(r'topics\s*=\s*(.*?)(?:,\s*\w+\s*=|$)', annotation, re.DOTALL)
            if tm:
                for t in re.findall(r'"([^"]+)"', tm.group(1)):
                    kafka_sub.add(t)
                # Also resolve constant reference: topics = SOME_CONST
                cm = re.search(r'^([A-Z_][A-Z_0-9]+)\s*$', tm.group(1).strip())
                if cm and cm.group(1) in topic_constants:
                    kafka_sub.add(topic_constants[cm.group(1)])

    return feign_clients, kafka_pub, kafka_sub

# ── 4. CLASSIFY SERVICE TYPE ─────────────────────────────────────────────────

CORE_NAMES = {'api-gateway', 'auth-server', 'auth-service',
              'catalog-service', 'shopping-cart-service',
              'customer-service', 'payment-service'}

CLOUD_NAMES = {'service-discovery', 'cloud-config', 'spring-server'}

def classify(name, props):
    if name in CLOUD_NAMES: return 'cloud'
    if 'gateway' in name:   return 'gateway'
    if name in CORE_NAMES:  return 'core'
    return 'extended'

# ── 5. BUILD SERVICE GRAPH ────────────────────────────────────────────────────

def build_graph():
    services = {}   # name → metadata dict
    connections = []

    # Static infra nodes
    infra = [
        {'id':'mysql',      'name':'MySQL 8.0',    'port':'3306',  'type':'db',
         'tag':'Per-service schema','icon':'◨',
         'tip':'MySQL 8.0. 10 databases — one per service. init-db.sql creates all schemas.'},
        {'id':'redis',      'name':'Redis 7.2',    'port':'6379',  'type':'db',
         'tag':'Cache · Session','icon':'◩',
         'tip':'Used by catalog-service (@Cacheable) and shopping-cart-service.'},
        {'id':'kafka',      'name':'Kafka 7.5',    'port':'9092',  'type':'mq',
         'tag':'3 topics','icon':'◧',
         'tip':'order-events (3p), payment-events (3p), inventory-events (3p). JSON serialization.'},
        {'id':'zookeeper',  'name':'ZooKeeper',    'port':'2181',  'type':'mq',
         'tag':'Kafka Coord.','icon':'◫',
         'tip':'ZooKeeper for Kafka cluster coordination.'},
        # Observability stack — always-on
        {'id':'zipkin',     'name':'Zipkin',       'port':'9411',  'type':'obs',
         'tag':'Distributed Traces','icon':'◈',
         'tip':'Available for distributed traces. Services emit B3 traces when SPRING_ZIPKIN_ENABLED=true; Sleuth sampling is configured at 100% once tracing is enabled.'},
        {'id':'prometheus', 'name':'Prometheus',   'port':'9090',  'type':'obs',
         'tag':'Metrics Scrape','icon':'◈',
         'tip':'Scrapes /actuator/prometheus from all 12 services every 15s. Feeds Grafana.'},
        {'id':'grafana',    'name':'Grafana',      'port':'3000',  'type':'obs',
         'tag':'Dashboards','icon':'◈',
         'tip':'Auto-provisioned with Prometheus datasource + ecommerce-overview dashboard (8 panels). admin/admin.'},
        {'id':'kafka-ui',   'name':'Kafka UI',     'port':'8080',  'type':'obs',
         'tag':'Topic Monitor','icon':'◈',
         'tip':'provectuslabs/kafka-ui — browse topics, partitions, consumer group lag in real time.'},
    ]
    for n in infra:
        services[n['id']] = n

    # Scan service dirs
    for d in find_service_dirs():
        name = d.name
        props = read_props(d)

        app_name = props.get('spring.application.name', name)
        port     = props.get('server.port', '?')
        stype    = classify(name, props)

        feign_clients, kafka_pub, kafka_sub = scan_java(d)

        # Determine dependency flags from docker-compose env patterns
        # (supplemented by what we found in Java)
        uses_kafka = bool(kafka_pub or kafka_sub)
        uses_redis = 'redis' in str(props)
        uses_mysql = 'datasource' in str(props)

        tip_parts = []
        if feign_clients:
            tip_parts.append(f"Feign → {', '.join(feign_clients)}")
        if kafka_pub:
            tip_parts.append(f"Publishes: {', '.join(sorted(kafka_pub))}")
        if kafka_sub:
            tip_parts.append(f"Consumes: {', '.join(sorted(kafka_sub))}")

        tag_parts = []
        if feign_clients: tag_parts.append('Feign')
        if kafka_pub:     tag_parts.append('Kafka pub')
        if kafka_sub:     tag_parts.append('Kafka sub')
        if uses_redis:    tag_parts.append('Redis')
        if not tag_parts: tag_parts.append(stype.capitalize())

        services[app_name] = {
            'id':    app_name,
            'name':  app_name,
            'port':  port,
            'type':  stype,
            'tag':   ' · '.join(tag_parts[:3]),
            'icon':  _icon(stype),
            'tip':   '; '.join(tip_parts) if tip_parts else f'{stype} service on :{port}',
            'feign': feign_clients,
            'pub':   sorted(kafka_pub),
            'sub':   sorted(kafka_sub),
            'mysql': uses_mysql,
            'redis': uses_redis,
        }

    # Build connection list
    for sname, svc in services.items():
        if svc['type'] in ('db','mq'): continue

        # Feign connections
        for target in svc.get('feign', []):
            # Normalize: strip -service suffix variants
            for tname in services:
                if target in tname or tname in target:
                    connections.append({'from': sname, 'to': tname, 'type': 'feign'})
                    break

        # Kafka publish → to kafka node
        if svc.get('pub'):
            connections.append({'from': sname, 'to': 'kafka', 'type': 'kafka-pub',
                                 'topics': svc['pub']})

        # Kafka subscribe → from kafka node
        if svc.get('sub'):
            connections.append({'from': 'kafka', 'to': sname, 'type': 'kafka-sub',
                                 'topics': svc['sub']})

        # DB connections
        if svc.get('mysql'):
            connections.append({'from': sname, 'to': 'mysql', 'type': 'db'})
        if svc.get('redis'):
            connections.append({'from': sname, 'to': 'redis', 'type': 'db'})

    # Static observability connections
    connections.append({'from': 'grafana',  'to': 'prometheus', 'type': 'obs'})
    connections.append({'from': 'kafka-ui', 'to': 'kafka',      'type': 'obs'})

    return services, connections

def _icon(t):
    return {'gateway':'◆','core':'◉','extended':'◬','cloud':'◎','db':'◨','mq':'◧','obs':'◈'}.get(t,'●')

# ── 6. COMPUTE LAYOUT ─────────────────────────────────────────────────────────

def compute_layout(services):
    """
    Assign (x, y) to each service in the 1320×800 design space.
    Columns within each row are evenly spaced.
    """
    W = 1320

    # Sort services into buckets
    buckets = {t: [] for t in ['gateway','core','extended','cloud','db','mq','obs']}
    for name, svc in services.items():
        t = svc['type']
        if t in buckets:
            buckets[t].append(name)

    positions = {}

    # Gateway (center top)
    for name in buckets['gateway']:
        positions[name] = (W // 2, 85)

    # Core services
    cores = sorted(buckets['core'])
    if cores:
        n = len(cores)
        xpad = min(180, (W - 240) // (n + 1))
        xstart = (W - xpad*(n-1)) // 2
        for i, name in enumerate(cores):
            positions[name] = (xstart + i*xpad, 250)

    # Extended services
    exts = sorted(buckets['extended'])
    if exts:
        n = len(exts)
        xpad = min(180, (W - 240) // (n + 1))
        xstart = (W - xpad*(n-1)) // 2
        for i, name in enumerate(exts):
            positions[name] = (xstart + i*xpad, 420)

    # Spring Cloud — fixed left column
    clouds = sorted(buckets['cloud'])
    for i, name in enumerate(clouds):
        positions[name] = (80, 270 + i * 90)

    # DB — MySQL, Redis (left half of infra row)
    dbs = sorted(buckets['db'])
    db_xs = [290, 470]
    for i, name in enumerate(dbs):
        positions[name] = (db_xs[i] if i < len(db_xs) else 290 + i*180, 570)

    # MQ — Kafka, ZooKeeper (right half of infra row)
    mqs = sorted(buckets['mq'])
    mq_xs = [660, 840]
    for i, name in enumerate(mqs):
        positions[name] = (mq_xs[i] if i < len(mq_xs) else 660 + i*180, 570)

    # Observability row — Zipkin, Prometheus, Grafana, Kafka UI
    obs_order = ['zipkin', 'prometheus', 'grafana', 'kafka-ui']
    obs_all = obs_order + [n for n in sorted(buckets['obs']) if n not in obs_order]
    n = len(obs_all)
    xpad = min(200, (W - 240) // (n + 1))
    xstart = (W - xpad*(n-1)) // 2
    for i, name in enumerate(obs_all):
        if name in services:
            positions[name] = (xstart + i*xpad, 700)

    return positions

# ── 7. GENERATE HTML ──────────────────────────────────────────────────────────

TYPE_COLOR = {
    'gateway': ('var(--amber)',  '240,160,0',  'node-gateway'),
    'core':    ('var(--cyan)',   '0,200,240',  'node-core'),
    'extended':('var(--purple)', '157,120,255','node-ext'),
    'cloud':   ('var(--green)',  '0,228,144',  'node-cloud'),
    'db':      ('var(--rose)',   '255,77,109', 'node-db'),
    'mq':      ('var(--orange)', '255,107,0',  'node-mq'),
    'obs':     ('var(--teal)',   '0,210,190',  'node-obs'),
}

def make_node_html(svc, x, y, delay):
    css_class = TYPE_COLOR.get(svc['type'], ('','','node-core'))[2]
    tip = svc['tip'].replace('"','&quot;').replace("'","&#39;")
    width = 148 if svc['type'] == 'gateway' else (120 if svc['type'] == 'cloud' else 130)
    return f'''  <div class="node {css_class}"
       style="left:{x}px;top:{y}px;animation-delay:{delay:.2f}s;width:{width}px"
       data-tip="{tip}">
    <div class="node-header">
      <div class="node-icon">{svc['icon']}</div>
      <div class="node-name">{svc['name']}</div>
    </div>
    <div class="node-port">:{svc['port']}</div>
    <div class="node-tag">{svc['tag']}</div>
  </div>'''

def make_svg_path(conn, positions):
    """Generate SVG <path> elements for a connection."""
    frm = conn['from']
    to  = conn['to']
    ctype = conn['type']
    if frm not in positions or to not in positions:
        return ''

    x1,y1 = positions[frm]
    x2,y2 = positions[to]

    # Choose connection points based on relative position
    if abs(x1-x2) > abs(y1-y2)*1.5:  # mostly horizontal
        sx1 = x1 + (64 if x2 > x1 else -64)
        sy1 = y1 + (-6 if ctype=='feign' else 6)
        sx2 = x2 + (-64 if x2 > x1 else 64)
        sy2 = y2 + (-6 if ctype=='feign' else 6)
    elif y2 > y1:  # going down
        sx1, sy1 = x1, y1 + 26
        sx2, sy2 = x2, y2 - 26
    else:  # going up
        sx1, sy1 = x1, y1 - 26
        sx2, sy2 = x2, y2 + 26

    # Control points for cubic bezier
    mid_y = (sy1 + sy2) / 2
    d = f"M {sx1},{sy1} C {sx1},{mid_y} {sx2},{mid_y} {sx2},{sy2}"

    css = {
        'route':     'path-route',
        'feign':     'path-feign active',
        'kafka-pub': 'path-kafka-pub active',
        'kafka-sub': 'path-kafka-sub active',
        'db':        'path-db',
        'cloud':     'path-cloud',
        'obs':       'path-obs',
    }.get(ctype, 'path-db')

    markers = {
        'feign':     ' marker-end="url(#af)"',
        'kafka-pub': ' marker-end="url(#ak)"',
        'kafka-sub': ' marker-end="url(#ak)"',
        'route':     ' marker-end="url(#ar)"',
    }.get(ctype, '')

    return f'    <path class="{css}" d="{d}"{markers}/>'

def make_particle_paths(connections, positions):
    """Return JS array literal of path definitions for the particle system."""
    particles = []
    for conn in connections:
        ctype = conn['type']
        if ctype not in ('feign','kafka-pub','kafka-sub'): continue
        frm, to = conn['from'], conn['to']
        if frm not in positions or to not in positions: continue
        x1,y1 = positions[frm]; x2,y2 = positions[to]

        if abs(x1-x2) > abs(y1-y2)*1.5:
            sx1=x1+(64 if x2>x1 else -64); sy1=y1+(-6 if ctype=='feign' else 6)
            sx2=x2+(-64 if x2>x1 else 64); sy2=y2+(-6 if ctype=='feign' else 6)
        elif y2 > y1:
            sx1,sy1=x1,y1+26; sx2,sy2=x2,y2-26
        else:
            sx1,sy1=x1,y1-26; sx2,sy2=x2,y2+26
        my=(sy1+sy2)/2
        pts=[[sx1,sy1],[sx1,my],[sx2,my],[sx2,sy2]]

        color = '#00c8f0' if ctype=='feign' else ('#ff6b00' if ctype=='kafka-pub' else '#ff9900')
        speed = 0.9 if ctype=='feign' else (1.1 if ctype=='kafka-pub' else 0.95)
        r     = 3.0 if ctype=='feign' else 3.5
        count = 2 if ctype=='feign' else 3
        particles.append({'pts':pts,'color':color,'speed':speed,'r':r,'count':count})

    return json.dumps(particles, indent=4)

# ── 8. COUNT STATS ────────────────────────────────────────────────────────────

def count_tests():
    total = 0
    for f in ROOT.rglob('*Test.java'):
        if 'target' in str(f): continue
        src = f.read_text(errors='ignore')
        total += src.count('@Test')
    return total

def count_topics(services):
    topics = set()
    for svc in services.values():
        topics.update(svc.get('pub',[]))
        topics.update(svc.get('sub',[]))
    return len(topics)

def count_dbs(services):
    return sum(1 for s in services.values() if s.get('mysql'))

def count_obs(services):
    return sum(1 for s in services.values() if s['type'] == 'obs')

# ── 9. RENDER ─────────────────────────────────────────────────────────────────

def render(services, connections, positions):
    test_count  = count_tests()
    topic_count = count_topics(services)
    db_count    = count_dbs(services)
    obs_count   = count_obs(services)
    svc_count   = sum(1 for s in services.values() if s['type'] not in ('db','mq','obs'))
    particle_js = make_particle_paths(connections, positions)

    # Build node HTML with staggered delays by type
    type_order = ['cloud','gateway','core','extended','db','mq','obs']
    delay_counter = [0.0]
    def next_delay(base):
        d = base + delay_counter[0] * 0.08
        delay_counter[0] += 1
        return d
    type_base = {'cloud':0.05,'gateway':0.1,'core':0.3,'extended':0.7,'db':0.02,'mq':0.02}

    nodes_html = []
    for t in type_order:
        for name, svc in sorted(services.items()):
            if svc['type'] != t: continue
            if name not in positions: continue
            x, y = positions[name]
            delay = next_delay(type_base.get(t, 0.1))
            nodes_html.append(make_node_html(svc, x, y, delay))

    paths_html = []
    for conn in connections:
        p = make_svg_path(conn, positions)
        if p: paths_html.append(p)

    # Kafka topic chips
    kafka_topics = set()
    for svc in services.values():
        kafka_topics.update(svc.get('pub',[]))
        kafka_topics.update(svc.get('sub',[]))
    # Position topic chips near kafka node
    kx, ky = positions.get('kafka', (690, 570))
    topic_chips = []
    offsets = [(-100,-60),(60,-55),(-140,-30),(80,-25),(-60,-85)]
    for i, topic in enumerate(sorted(kafka_topics)):
        ox, oy = offsets[i % len(offsets)]
        d = 1.8 + i * 0.25
        topic_chips.append(
            f'  <div class="topic-chip" style="left:{kx+ox}px;top:{ky+oy}px;'
            f'animation-delay:{d}s">{topic}</div>'
        )

    return HTML_TEMPLATE.format(
        svc_count=svc_count,
        topic_count=topic_count,
        db_count=db_count,
        obs_count=obs_count,
        test_count=test_count,
        nodes_html='\n'.join(nodes_html),
        paths_html='\n'.join(paths_html),
        topic_chips='\n'.join(topic_chips),
        particle_js=particle_js,
    )

# ── 10. HTML TEMPLATE ─────────────────────────────────────────────────────────

HTML_TEMPLATE = r"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Ecommerce Distributed System — Architecture</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Chakra+Petch:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
<style>
*,*::before,*::after{{box-sizing:border-box;margin:0;padding:0}}
:root{{
  --bg:#03080f;--bg2:#060e1a;--bg3:#0b1628;--border:#112038;--border2:#1a3050;
  --text:#b8cfe8;--text-dim:#3d5a7a;
  --cyan:#00c8f0;--amber:#f0a000;--green:#00e490;--purple:#9d78ff;
  --rose:#ff4d6d;--orange:#ff6b00;--teal:#00d2be;
}}
html,body{{width:100%;min-height:100vh;background:var(--bg);color:var(--text);
  font-family:'Chakra Petch',sans-serif;overflow-x:hidden}}
body::before{{content:'';position:fixed;inset:0;z-index:0;pointer-events:none;
  background-image:linear-gradient(rgba(0,200,240,.025)1px,transparent 1px),
    linear-gradient(90deg,rgba(0,200,240,.025)1px,transparent 1px);
  background-size:40px 40px}}
body::after{{content:'';position:fixed;inset:0;z-index:0;pointer-events:none;
  background:radial-gradient(ellipse 70% 60% at 50% 40%,rgba(0,100,160,.08)0%,transparent 70%)}}
header{{position:relative;z-index:10;padding:32px 48px 20px;border-bottom:1px solid var(--border);
  display:flex;align-items:flex-end;justify-content:space-between}}
.header-left{{display:flex;flex-direction:column;gap:6px}}
.label-tag{{font-family:'JetBrains Mono',monospace;font-size:10px;font-weight:500;
  letter-spacing:.2em;text-transform:uppercase;color:var(--cyan);opacity:.7;
  display:flex;align-items:center;gap:8px}}
.label-tag::before{{content:'';display:block;width:20px;height:1px;background:var(--cyan);opacity:.5}}
h1{{font-size:clamp(22px,3vw,34px);font-weight:700;letter-spacing:.04em;color:#fff;line-height:1}}
h1 span{{color:var(--cyan)}}
.subtitle{{font-family:'JetBrains Mono',monospace;font-size:11px;color:var(--text-dim);
  letter-spacing:.05em;margin-top:4px}}
.header-right{{display:flex;align-items:center;gap:20px;flex-wrap:wrap;justify-content:flex-end}}
.stat-chip{{display:flex;flex-direction:column;align-items:center;gap:2px;
  background:var(--bg3);border:1px solid var(--border2);padding:8px 16px;border-radius:4px}}
.stat-chip .num{{font-size:20px;font-weight:700;font-family:'JetBrains Mono',monospace;
  color:var(--cyan);line-height:1}}
.stat-chip .lbl{{font-size:9px;color:var(--text-dim);letter-spacing:.12em;text-transform:uppercase}}
.live-badge{{display:flex;align-items:center;gap:7px;font-size:10px;
  font-family:'JetBrains Mono',monospace;letter-spacing:.1em;color:var(--green);
  padding:6px 12px;border:1px solid rgba(0,228,144,.3);border-radius:20px;
  background:rgba(0,228,144,.06)}}
.live-dot{{width:7px;height:7px;border-radius:50%;background:var(--green);
  animation:pulse-dot 2s ease-in-out infinite}}
@keyframes pulse-dot{{0%,100%{{box-shadow:0 0 0 0 rgba(0,228,144,.5)}}50%{{box-shadow:0 0 0 5px rgba(0,228,144,0)}}}}
.legend{{position:relative;z-index:10;padding:12px 48px;border-bottom:1px solid var(--border);
  display:flex;gap:24px;flex-wrap:wrap;align-items:center}}
.legend-item{{display:flex;align-items:center;gap:7px;cursor:pointer;transition:opacity .2s}}
.legend-item:hover{{opacity:.7}}
.legend-line{{width:28px;height:2px;border-radius:2px}}
.legend-line.feign{{background-image:repeating-linear-gradient(90deg,var(--cyan)0,var(--cyan)6px,transparent 6px,transparent 10px)}}
.legend-line.kafka{{background-image:repeating-linear-gradient(90deg,var(--orange)0,var(--orange)5px,transparent 5px,transparent 8px)}}
.legend-line.route{{background-image:repeating-linear-gradient(90deg,var(--amber)0,var(--amber)4px,transparent 4px,transparent 7px)}}
.legend-line.db{{background:var(--border2);height:1px;opacity:.7}}
.legend-dot{{width:10px;height:10px;border-radius:50%;border:2px solid}}
.legend-label{{font-size:10px;color:var(--text-dim);letter-spacing:.05em;font-family:'JetBrains Mono',monospace}}
.flow-controls{{position:relative;z-index:10;padding:12px 48px;border-bottom:1px solid var(--border);
  display:flex;gap:12px;align-items:center}}
.flow-btn{{font-family:'Chakra Petch',sans-serif;font-size:11px;font-weight:600;
  letter-spacing:.08em;text-transform:uppercase;padding:7px 16px;border-radius:4px;
  cursor:pointer;border:1px solid;transition:all .2s;background:transparent}}
.flow-btn.checkout{{color:var(--cyan);border-color:rgba(0,200,240,.4)}}
.flow-btn.checkout:hover,.flow-btn.checkout.active{{background:rgba(0,200,240,.12);box-shadow:0 0 12px rgba(0,200,240,.2)}}
.flow-btn.kafka-flow{{color:var(--orange);border-color:rgba(255,107,0,.4)}}
.flow-btn.kafka-flow:hover,.flow-btn.kafka-flow.active{{background:rgba(255,107,0,.12);box-shadow:0 0 12px rgba(255,107,0,.2)}}
.flow-btn.reset{{color:var(--text-dim);border-color:var(--border2)}}
.flow-btn.reset:hover{{color:var(--text);background:var(--bg3)}}
.flow-label{{font-size:10px;color:var(--text-dim);font-family:'JetBrains Mono',monospace}}
.diagram-wrapper{{position:relative;z-index:5;padding:20px 24px 0;overflow-x:auto}}
.diagram{{position:relative;width:1320px;height:800px;margin:0 auto;
  animation:diagram-in .6s ease both}}
@keyframes diagram-in{{from{{opacity:0;transform:translateY(16px)}}to{{opacity:1;transform:translateY(0)}}}}
.band{{position:absolute;left:0;right:0;height:1px;
  background:linear-gradient(90deg,transparent,var(--border2)20%,var(--border2)80%,transparent)}}
.section-label{{position:absolute;font-family:'JetBrains Mono',monospace;font-size:8px;
  letter-spacing:.15em;text-transform:uppercase;color:var(--text-dim)}}
#conn-svg{{position:absolute;inset:0;width:100%;height:100%;pointer-events:none;overflow:visible}}
.path-route{{fill:none;stroke:var(--amber);stroke-width:1.5;opacity:.4;stroke-dasharray:5 4;
  animation:dash-flow 2s linear infinite}}
.path-feign{{fill:none;stroke:var(--cyan);stroke-width:2;opacity:.7;stroke-dasharray:8 5;
  animation:dash-flow 1.2s linear infinite}}
.path-kafka-pub{{fill:none;stroke:var(--orange);stroke-width:2;opacity:.8;stroke-dasharray:6 4;
  animation:dash-flow .9s linear infinite}}
.path-kafka-sub{{fill:none;stroke:var(--orange);stroke-width:2;opacity:.8;stroke-dasharray:6 4;
  animation:dash-flow .9s linear infinite reverse}}
.path-db{{fill:none;stroke:var(--border2);stroke-width:1;opacity:.4;stroke-dasharray:3 6}}
.path-cloud{{fill:none;stroke:var(--green);stroke-width:1;opacity:.2;stroke-dasharray:4 6}}
.path-obs{{fill:none;stroke:var(--teal);stroke-width:1.5;opacity:.5;stroke-dasharray:5 5}}
@keyframes dash-flow{{from{{stroke-dashoffset:0}}to{{stroke-dashoffset:-30}}}}
.arrow-route{{fill:var(--amber)}}
.arrow-feign{{fill:var(--cyan)}}
.arrow-kafka{{fill:var(--orange)}}
.node{{position:absolute;transform:translate(-50%,-50%);display:flex;flex-direction:column;
  background:var(--bg3);border:1px solid var(--border2);border-radius:6px;
  padding:8px 10px 7px;cursor:default;
  transition:transform .2s ease,box-shadow .2s ease,border-color .2s ease;
  opacity:0;animation:node-appear .4s ease forwards}}
.node:hover{{transform:translate(-50%,-50%) translateY(-3px) scale(1.04);z-index:100;
  border-color:var(--node-color,var(--cyan));
  box-shadow:0 0 20px rgba(var(--node-rgb,0,200,240),.3),0 8px 24px rgba(0,0,0,.6)}}
@keyframes node-appear{{from{{opacity:0;transform:translate(-50%,-50%) scale(.85)}}
  to{{opacity:1;transform:translate(-50%,-50%) scale(1)}}}}
.node::before{{content:'';position:absolute;left:0;top:0;bottom:0;width:3px;
  background:var(--node-color,var(--cyan));border-radius:6px 0 0 6px;
  box-shadow:0 0 8px var(--node-color,var(--cyan))}}
.node-header{{display:flex;align-items:center;gap:6px;margin-bottom:4px}}
.node-icon{{font-size:10px;color:var(--node-color,var(--cyan));flex-shrink:0}}
.node-name{{font-size:10px;font-weight:600;letter-spacing:.03em;color:#e2eeff;
  white-space:nowrap;overflow:hidden;text-overflow:ellipsis;line-height:1.2}}
.node-port{{font-family:'JetBrains Mono',monospace;font-size:9px;
  color:var(--node-color,var(--cyan));opacity:.7;
  background:rgba(var(--node-rgb,0,200,240),.08);padding:1px 5px;border-radius:3px;
  border:1px solid rgba(var(--node-rgb,0,200,240),.2);display:inline-block;
  margin-top:1px;width:fit-content}}
.node-tag{{font-size:8px;letter-spacing:.08em;text-transform:uppercase;
  color:var(--text-dim);margin-top:2px}}
.node-gateway{{--node-color:var(--amber);--node-rgb:240,160,0}}
.node-core{{--node-color:var(--cyan);--node-rgb:0,200,240}}
.node-ext{{--node-color:var(--purple);--node-rgb:157,120,255}}
.node-cloud{{--node-color:var(--green);--node-rgb:0,228,144}}
.node-db{{--node-color:var(--rose);--node-rgb:255,77,109}}
.node-mq{{--node-color:var(--orange);--node-rgb:255,107,0}}
.node-obs{{--node-color:var(--teal);--node-rgb:0,210,190}}
.topic-chip{{position:absolute;transform:translateX(-50%);background:rgba(255,107,0,.1);
  border:1px dashed rgba(255,107,0,.5);border-radius:3px;padding:3px 8px;
  font-family:'JetBrains Mono',monospace;font-size:9px;letter-spacing:.06em;
  color:var(--orange);white-space:nowrap;
  opacity:0;animation:node-appear .4s ease forwards,topic-pulse 3s .4s ease-in-out infinite}}
@keyframes topic-pulse{{0%,100%{{box-shadow:0 0 4px rgba(255,107,0,.2)}}50%{{box-shadow:0 0 10px rgba(255,107,0,.5)}}}}
#particle-canvas{{position:absolute;inset:0;pointer-events:none;width:100%;height:100%}}
.tooltip{{position:fixed;z-index:9999;pointer-events:none;background:#0a1525;
  border:1px solid var(--border2);border-radius:6px;padding:10px 14px;max-width:280px;
  box-shadow:0 8px 32px rgba(0,0,0,.8);opacity:0;transition:opacity .15s;
  font-size:11px;line-height:1.6}}
.tooltip.visible{{opacity:1}}
.tooltip-title{{font-weight:600;color:#fff;margin-bottom:6px;font-size:12px}}
.tooltip-row{{color:var(--text-dim)}}
.tooltip-row span{{color:var(--text)}}
footer{{position:relative;z-index:10;padding:20px 48px;border-top:1px solid var(--border);
  display:flex;gap:32px;flex-wrap:wrap;align-items:center;justify-content:space-between;margin-top:20px}}
.tech-stack{{display:flex;gap:10px;flex-wrap:wrap}}
.tech-badge{{font-family:'JetBrains Mono',monospace;font-size:10px;letter-spacing:.06em;
  padding:4px 10px;border-radius:3px;border:1px solid;background:transparent}}
.tech-badge.java{{color:#f89820;border-color:rgba(248,152,32,.3)}}
.tech-badge.spring{{color:#6db33f;border-color:rgba(109,179,63,.3)}}
.tech-badge.kafka{{color:var(--orange);border-color:rgba(255,107,0,.3)}}
.tech-badge.redis{{color:#dc382d;border-color:rgba(220,56,45,.3)}}
.tech-badge.mysql{{color:#4479a1;border-color:rgba(68,121,161,.3)}}
.tech-badge.docker{{color:#2496ed;border-color:rgba(36,150,237,.3)}}
.gen-note{{font-size:10px;color:var(--text-dim);font-family:'JetBrains Mono',monospace}}
.diagram.show-checkout .path-route,.diagram.show-checkout .path-db,
.diagram.show-checkout .path-kafka-pub,.diagram.show-checkout .path-kafka-sub{{opacity:.06}}
.diagram.show-checkout .path-feign.active{{opacity:1;stroke-width:2.5}}
.diagram.show-checkout .path-feign:not(.active){{opacity:.1}}
.diagram.show-kafka .path-route,.diagram.show-kafka .path-db,
.diagram.show-kafka .path-feign{{opacity:.06}}
.diagram.show-kafka .path-kafka-pub.active,.diagram.show-kafka .path-kafka-sub.active{{opacity:1;stroke-width:2.5}}
.diagram.show-kafka .path-kafka-pub:not(.active),.diagram.show-kafka .path-kafka-sub:not(.active){{opacity:.08}}
@media(max-width:900px){{
  header{{padding:20px 20px 16px;flex-direction:column;align-items:flex-start;gap:12px}}
  .legend,.flow-controls,footer{{padding-left:20px;padding-right:20px}}
  .diagram-wrapper{{padding:12px 8px 0}}
}}
</style>
</head>
<body>
<header>
  <div class="header-left">
    <div class="label-tag">System Architecture</div>
    <h1>ECOMMERCE <span>DISTRIBUTED</span> SYSTEM</h1>
    <div class="subtitle">Spring Boot 2.5.7 · Spring Cloud 2020.0.4 · Java 11 · Auto-generated</div>
  </div>
  <div class="header-right">
    <div class="stat-chip"><div class="num">{svc_count}</div><div class="lbl">Services</div></div>
    <div class="stat-chip"><div class="num">{topic_count}</div><div class="lbl">Kafka Topics</div></div>
    <div class="stat-chip"><div class="num">{db_count}</div><div class="lbl">Databases</div></div>
    <div class="stat-chip"><div class="num">{obs_count}</div><div class="lbl">Observability</div></div>
    <div class="stat-chip"><div class="num">{test_count}</div><div class="lbl">Tests</div></div>
    <div class="live-badge"><div class="live-dot"></div>LIVE MAP</div>
  </div>
</header>
<div class="legend">
  <div class="legend-item"><div class="legend-line route"></div><div class="legend-label">API Gateway Route</div></div>
  <div class="legend-item"><div class="legend-line feign"></div><div class="legend-label">Feign (HTTP)</div></div>
  <div class="legend-item"><div class="legend-line kafka"></div><div class="legend-label">Kafka Event</div></div>
  <div class="legend-item"><div class="legend-line db"></div><div class="legend-label">Database</div></div>
  <div style="margin-left:8px;display:flex;gap:14px">
    <div class="legend-item"><div class="legend-dot" style="border-color:var(--amber)"></div><div class="legend-label">Gateway</div></div>
    <div class="legend-item"><div class="legend-dot" style="border-color:var(--cyan)"></div><div class="legend-label">Core</div></div>
    <div class="legend-item"><div class="legend-dot" style="border-color:var(--purple)"></div><div class="legend-label">Extended</div></div>
    <div class="legend-item"><div class="legend-dot" style="border-color:var(--green)"></div><div class="legend-label">Spring Cloud</div></div>
    <div class="legend-item"><div class="legend-dot" style="border-color:var(--rose)"></div><div class="legend-label">Data</div></div>
    <div class="legend-item"><div class="legend-dot" style="border-color:var(--orange)"></div><div class="legend-label">Messaging</div></div>
    <div class="legend-item"><div class="legend-dot" style="border-color:var(--teal)"></div><div class="legend-label">Observability</div></div>
  </div>
</div>
<div class="flow-controls">
  <span class="flow-label">Highlight flow:</span>
  <button class="flow-btn checkout" id="btn-checkout">⟳ Checkout Flow</button>
  <button class="flow-btn kafka-flow" id="btn-kafka">⟳ Kafka Events</button>
  <button class="flow-btn reset" id="btn-reset">Reset</button>
</div>
<div class="diagram-wrapper">
<div class="diagram" id="diagram">
  <div class="band" style="top:148px"></div>
  <div class="band" style="top:318px"></div>
  <div class="band" style="top:490px"></div>
  <div class="band" style="top:622px"></div>
  <div class="section-label" style="top:132px;left:10px">GATEWAY LAYER</div>
  <div class="section-label" style="top:200px;left:10px">CORE SERVICES</div>
  <div class="section-label" style="top:368px;left:10px">EXTENDED SERVICES</div>
  <div class="section-label" style="top:500px;left:10px">INFRASTRUCTURE</div>
  <div class="section-label" style="top:634px;left:10px">OBSERVABILITY</div>
  <div class="section-label" style="top:184px;left:1080px">SPRING CLOUD</div>

  <svg id="conn-svg" viewBox="0 0 1320 800">
    <defs>
      <marker id="ar" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto">
        <path d="M0,0 L6,3 L0,6 Z" class="arrow-route" opacity=".7"/>
      </marker>
      <marker id="af" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto">
        <path d="M0,0 L6,3 L0,6 Z" class="arrow-feign"/>
      </marker>
      <marker id="ak" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto">
        <path d="M0,0 L6,3 L0,6 Z" class="arrow-kafka"/>
      </marker>
    </defs>
{paths_html}
  </svg>

{topic_chips}
{nodes_html}
  <canvas id="particle-canvas"></canvas>
</div>
</div>
<footer>
  <div class="tech-stack">
    <span class="tech-badge java">Java 11</span>
    <span class="tech-badge spring">Spring Boot 2.5.7</span>
    <span class="tech-badge spring">Spring Cloud 2020.0.4</span>
    <span class="tech-badge kafka">Apache Kafka</span>
    <span class="tech-badge redis">Redis</span>
    <span class="tech-badge mysql">MySQL 8.0</span>
    <span class="tech-badge docker">Docker Compose</span>
  </div>
  <div class="gen-note">⚙ Auto-generated by generate-arch.py — run again after any service change</div>
</footer>
<div class="tooltip" id="tooltip"></div>
<script>
const tooltip=document.getElementById('tooltip');
document.querySelectorAll('[data-tip]').forEach(node=>{{
  const name=node.querySelector('.node-name')?.textContent||'';
  const port=node.querySelector('.node-port')?.textContent||'';
  const tag=node.querySelector('.node-tag')?.textContent||'';
  const desc=node.dataset.tip;
  node.addEventListener('mouseenter',e=>{{
    tooltip.innerHTML=`<div class="tooltip-title">${{name}}</div>
      <div class="tooltip-row">Port: <span>${{port}}</span></div>
      <div class="tooltip-row">Role: <span>${{tag}}</span></div>
      <div class="tooltip-row" style="margin-top:6px;color:var(--text)">${{desc}}</div>`;
    tooltip.classList.add('visible');move(e);
  }});
  node.addEventListener('mousemove',move);
  node.addEventListener('mouseleave',()=>tooltip.classList.remove('visible'));
}});
function move(e){{
  const x=e.clientX+14,y=e.clientY+14;
  const w=tooltip.offsetWidth,h=tooltip.offsetHeight;
  tooltip.style.left=(x+w>window.innerWidth?x-w-28:x)+'px';
  tooltip.style.top=(y+h>window.innerHeight?y-h-28:y)+'px';
}}
const diagram=document.getElementById('diagram');
document.getElementById('btn-checkout').addEventListener('click',function(){{
  diagram.classList.toggle('show-checkout');diagram.classList.remove('show-kafka');
  this.classList.toggle('active');document.getElementById('btn-kafka').classList.remove('active');
}});
document.getElementById('btn-kafka').addEventListener('click',function(){{
  diagram.classList.toggle('show-kafka');diagram.classList.remove('show-checkout');
  this.classList.toggle('active');document.getElementById('btn-checkout').classList.remove('active');
}});
document.getElementById('btn-reset').addEventListener('click',()=>{{
  diagram.classList.remove('show-checkout','show-kafka');
  document.querySelectorAll('.flow-btn').forEach(b=>b.classList.remove('active'));
}});
const canvas=document.getElementById('particle-canvas');
const dg=document.getElementById('diagram');
function resizeCanvas(){{canvas.width=dg.offsetWidth;canvas.height=dg.offsetHeight}}
resizeCanvas();window.addEventListener('resize',resizeCanvas);
const ctx=canvas.getContext('2d');
function sc(){{return dg.offsetWidth/1320}}
const PATHS={particle_js};
function bezier(pts,t){{
  const[p0,p1,p2,p3]=pts,u=1-t;
  return[u*u*u*p0[0]+3*u*u*t*p1[0]+3*u*t*t*p2[0]+t*t*t*p3[0],
         u*u*u*p0[1]+3*u*u*t*p1[1]+3*u*t*t*p2[1]+t*t*t*p3[1]];
}}
const particles=[];
PATHS.forEach(path=>{{
  for(let i=0;i<path.count;i++){{
    particles.push({{path,t:i/path.count,speed:path.speed*(0.9+Math.random()*.2)*.003,
      r:path.r,color:path.color,alpha:0.85+Math.random()*.15}});
  }}
}});
function draw(){{
  ctx.clearRect(0,0,canvas.width,canvas.height);
  const s=sc();
  particles.forEach(p=>{{
    p.t+=p.speed;if(p.t>1)p.t-=1;
    const[x,y]=bezier(p.path.pts,p.t);
    const sx=x*s,sy=y*s;
    const g=ctx.createRadialGradient(sx,sy,0,sx,sy,p.r*3);
    g.addColorStop(0,p.color+'cc');g.addColorStop(1,p.color+'00');
    ctx.beginPath();ctx.arc(sx,sy,p.r*3,0,Math.PI*2);ctx.fillStyle=g;ctx.fill();
    ctx.beginPath();ctx.arc(sx,sy,p.r*s*.8,0,Math.PI*2);
    ctx.fillStyle=p.color;ctx.globalAlpha=p.alpha;ctx.fill();ctx.globalAlpha=1;
  }});
  requestAnimationFrame(draw);
}}
draw();
</script>
</body>
</html>"""

# ── MAIN ─────────────────────────────────────────────────────────────────────

if __name__ == '__main__':
    print("Scanning project...")
    services, connections = build_graph()
    positions = compute_layout(services)

    print(f"  Found {sum(1 for s in services.values() if s['type'] not in ('db','mq'))} services")
    print(f"  Found {sum(1 for c in connections if c['type']=='feign')} Feign connections")
    print(f"  Found {sum(1 for c in connections if 'kafka' in c['type'])} Kafka connections")
    print(f"  Tests: {count_tests()}")

    html = render(services, connections, positions)
    out = ROOT / 'architecture.html'
    out.write_text(html)
    print(f"\n✓ Written to {out}")
    print("  Open with: open architecture.html")

    # Print what was discovered
    print("\nServices discovered:")
    for name, svc in sorted(services.items(), key=lambda x: x[1]['type']):
        if svc['type'] not in ('db','mq'):
            feign = f" → feign:{svc.get('feign','')}" if svc.get('feign') else ''
            pub   = f" → pub:{svc.get('pub','')}"   if svc.get('pub')   else ''
            sub   = f" → sub:{svc.get('sub','')}"   if svc.get('sub')   else ''
            print(f"  [{svc['type']:8}] {name:30} :{svc['port']}{feign}{pub}{sub}")
