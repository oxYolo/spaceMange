import GeoJSON from 'ol/format/GeoJSON'
import WKT from 'ol/format/WKT'

/** 交给地图格式库处理环/洞/多面结构，避免手工括号造成非法 WKT。 */
export function geometryJsonToWkt(value: any): string {
  if (!value) return ''
  const geometry = new GeoJSON().readGeometry(value)
  return geometry ? new WKT().writeGeometry(geometry) : ''
}
