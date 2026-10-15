import gzip
import json
import statistics
from pathlib import Path
from playwright.sync_api import sync_playwright

salida=Path('/workspace/ondexia-vistas/escenario-landing/perfil')
salida.mkdir(parents=True,exist_ok=True)
with sync_playwright() as p:
    navegador=p.chromium.launch(executable_path='/usr/bin/chromium',args=['--no-sandbox'])
    pagina=navegador.new_page(viewport={'width':1440,'height':1000})
    pagina.goto('http://127.0.0.1:4324/',wait_until='networkidle')
    assert pagina.locator('.modo-scroll').count()==1
    sesion=pagina.context.new_cdp_session(pagina)
    sesion.send('Performance.enable')
    pagina.evaluate('''() => {
      window.tareasDelPerfil=[];
      window.observadorDelPerfil=new PerformanceObserver(lista=>window.tareasDelPerfil.push(...lista.getEntries().map(e=>({inicio:e.startTime,duracion:e.duration}))));
      window.observadorDelPerfil.observe({type:'longtask',buffered:false});
    }''')
    registros=[]
    for ronda in range(3):
        for con_morphing in [True,False]:
            pagina.evaluate('''habilitado => {
              const contorno=document.querySelector('[data-contorno]');
              if (habilitado) delete contorno.setAttribute;
              else contorno.setAttribute=function(nombre,valor){if(nombre!=='d') SVGElement.prototype.setAttribute.call(this,nombre,valor)};
              window.tareasDelPerfil=[];
            }''',con_morphing)
            eventos=[]
            recibir_lote=lambda lote:eventos.extend(lote['value'])
            sesion.on('Tracing.dataCollected',recibir_lote)
            sesion.send('Tracing.start',{'categories':'devtools.timeline','transferMode':'ReportEvents'})
            antes={m['name']:m['value'] for m in sesion.send('Performance.getMetrics')['metrics']}
            intervalos=pagina.evaluate('''async()=>{
              const tramo=document.querySelector('[data-recorrido]');
              const escenario=tramo.querySelector('[data-escenario]');
              const inicio=tramo.getBoundingClientRect().top+scrollY-parseFloat(getComputedStyle(escenario).top);
              const distancia=tramo.offsetHeight-escenario.offsetHeight;
              const intervalos=[];let anterior;
              for(let paso=0;paso<=240;paso++){
                await new Promise(resolve=>requestAnimationFrame(instante=>{if(anterior!==undefined)intervalos.push(instante-anterior);anterior=instante;resolve()}));
                const avance=paso<=120?paso/120:(240-paso)/120;
                scrollTo({top:inicio+distancia*avance,behavior:'instant'});
              }
              return intervalos;
            }''')
            despues={m['name']:m['value'] for m in sesion.send('Performance.getMetrics')['metrics']}
            listo=[]
            sesion.once('Tracing.tracingComplete',lambda evento:listo.append(True))
            sesion.send('Tracing.end')
            pagina.evaluate('() => new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))')
            for intento in range(100):
                if listo:break
                pagina.wait_for_timeout(20)
            assert listo
            datos={'ronda':ronda+1,'morphing':con_morphing,
              'intervalosCuadrosMs':{'mediana':statistics.median(intervalos),'maximo':max(intervalos),'mayores34ms':sum(x>34 for x in intervalos)},
              'metricas':{n:despues[n]-antes[n] for n in ['TaskDuration','ScriptDuration','LayoutDuration','RecalcStyleDuration','LayoutCount','RecalcStyleCount']},
              'tareasLargas':pagina.evaluate('window.tareasDelPerfil'),
              'pintura':{'eventos':sum(e.get('name')=='Paint' for e in eventos),'duracionMs':sum(e.get('dur',0)/1000 for e in eventos if e.get('name')=='Paint')}}
            registros.append(datos)
            with gzip.GzipFile(salida/f'traza-{ronda+1}-{int(con_morphing)}.json.gz','wb',mtime=0) as archivo:
                archivo.write(json.dumps(eventos,separators=(',',':')).encode())
            sesion.remove_listener('Tracing.dataCollected',recibir_lote)
            print(json.dumps(datos,ensure_ascii=False),flush=True)
    pagina.evaluate('delete document.querySelector("[data-contorno]").setAttribute')
    (salida/'resumen.json').write_text(json.dumps({'navegador':navegador.version,'ventana':{'ancho':1440,'alto':1000},'condiciones':'Chromium headless local sin limitar CPU. Tres pares de 240 cuadros: ida y vuelta. Sin descarga de recursos durante el tramo. Desactivar d es una mutación de medición en el DOM, restaurada; no una variante publicada. Orden con/sin repetido: no es benchmark causal ni acredita dispositivos físicos.','registros':registros},ensure_ascii=False,indent=2))
    navegador.close()
