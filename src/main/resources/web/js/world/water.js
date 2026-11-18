// One horizontal water surface per body, at its water level; the shoreline comes from the depth test.
import * as THREE from 'three';

export class WaterSurfaces {
  constructor(relief) {
    this.group = new THREE.Group(); this.group.name = 'water';
    this.time = { value: 0 }; this.sky = { value: new THREE.Color(0xa8c8e8) }; this.materials = [];
    const margin = 3, cell = relief.cell;
    for (const b of relief.boxes()) {
      if (b.r1 < 0) continue;
      const w = (b.c1 - b.c0 + 1 + 2 * margin) * cell, d = (b.r1 - b.r0 + 1 + 2 * margin) * cell;
      const geometry = new THREE.PlaneGeometry(w, d, 1, 1);
      geometry.rotateX(-Math.PI / 2);
      const material = new THREE.MeshStandardMaterial({ color: 0x3a86b4, emissive: 0x0d2c44, roughness: 0.08, metalness: 0.0, transparent: true, opacity: 0.82 });
      const time = this.time, sky = this.sky; this.materials.push(material);
      material.onBeforeCompile = (shader) => {
        shader.uniforms.uTime = time; shader.uniforms.uSky = sky;
        shader.vertexShader = shader.vertexShader.replace('#include <common>', '#include <common>\nvarying vec3 vWorldPos;')
          .replace('#include <begin_vertex>', '#include <begin_vertex>\nvWorldPos = (modelMatrix * vec4(transformed, 1.0)).xyz;');
        shader.fragmentShader = shader.fragmentShader.replace('#include <common>', `#include <common>
varying vec3 vWorldPos; uniform float uTime; uniform vec3 uSky;
vec3 ripple(vec2 p){
  float a = sin(p.x*0.55 + uTime*0.9) + sin(p.y*0.43 - uTime*0.7) + sin((p.x+p.y)*0.21 + uTime*0.5);
  float b = cos(p.x*0.37 - uTime*0.6) + cos(p.y*0.61 + uTime*0.8);
  return normalize(vec3(a*0.03, 1.0, b*0.03));
}`).replace('#include <normal_fragment_maps>', `#include <normal_fragment_maps>
normal = normalize((viewMatrix * vec4(ripple(vWorldPos.xz), 0.0)).xyz * 0.35 + normal * 0.65);`)
          .replace('#include <dithering_fragment>', `#include <dithering_fragment>
float fres = pow(1.0 - clamp(dot(normalize(cameraPosition - vWorldPos), vec3(0.0,1.0,0.0)), 0.0, 1.0), 3.0);
gl_FragColor.rgb = mix(gl_FragColor.rgb, uSky, clamp(0.25 + fres * 0.75, 0.0, 1.0));
gl_FragColor.a = mix(gl_FragColor.a, 0.96, fres);`);
      };
      const mesh = new THREE.Mesh(geometry, material);
      mesh.position.set(((b.c0 + b.c1 + 1) / 2) * cell, b.level, ((b.r0 + b.r1 + 1) / 2) * cell);
      mesh.renderOrder = 2; mesh.name = 'water' + b.id;
      this.group.add(mesh);
    }
  }
  update(seconds, skyColor, dayLight) {
    this.time.value = seconds;
    if (skyColor) this.sky.value.copy(skyColor).multiplyScalar(0.85);
    for (const m of this.materials) m.emissiveIntensity = 0.25 + 0.75 * (dayLight === undefined ? 1 : dayLight);
  }
}
