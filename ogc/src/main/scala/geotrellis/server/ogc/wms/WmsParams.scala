package geotrellis.server.ogc.wms

import geotrellis.server.ogc.wms.Constants.SUPPORTED_FORMATS
import geotrellis.server.ogc.params._

import cats._
import cats.implicits._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import Validated._
import geotrellis.proj4.CRS
import geotrellis.vector.Extent

import scala.util.Try

abstract sealed class WmsParams {
  val version: String
}

object WmsParams {
  final case class GetCapabilities(
    version: String,
    format: Option[String],
    updateSequence: Option[String]
  ) extends WmsParams

  object GetCapabilities {
    def build(params: ParamMap): ValidatedNel[ParamError, WmsParams] = {
      (params.validatedVersion("1.3.0"),
        params.validatedOptionalParam("format"),
        params.validatedOptionalParam("updatesequence")
      ).mapN(GetCapabilities.apply)
    }
  }

  case class GetMap(
    version: String,
    layers: Array[String],
    boundingBox: Extent,
    format: String,
    width: Int,
    height: Int,
    crs: CRS
  ) extends WmsParams

  object GetMap {
    def build(params: ParamMap): ValidatedNel[ParamError, WmsParams] = {
      val versionParam =
        params.validatedVersion("1.3.0")

      versionParam
        .andThen { version: String =>
          val layers =
            params.validatedParam[Array[String]]("layers", { s => Some(s.split(",")) })

          val bbox =
            params.validatedParam("bbox", {s =>
              s.split(",").map(_.toDouble) match {
                case Array(xmin, ymin, xmax, ymax) =>
                  Some(Extent(ymin, xmin, ymax, xmax))
                case _ => None
              }
            })

          val width =
            params.validatedParam[Int]("width", { s => Try(s.toInt).toOption })

          val height =
            params.validatedParam[Int]("height", { s => Try(s.toInt).toOption })

          val crs = params.validatedParam("crs", { s => Try(CRS.fromName(s)).toOption })

          val format =
            params.validatedParam("format")
              .andThen { f =>
                SUPPORTED_FORMATS.get(f) match {
                  case Some(format) => Valid(format).toValidatedNel
                  case None =>
                    Invalid(ParamError.UnsupportedFormatError(f)).toValidatedNel
                  }
              }

          (layers, bbox, format, width, height, crs).mapN { case (layers, bbox, format, width, height, crs) =>
            GetMap(version, layers, bbox, format = format, width = width, height = height, crs = crs)
          }
        }
    }
  }

  def apply(queryParams: Map[String, Seq[String]]): ValidatedNel[ParamError, WmsParams] = {
    val params = new ParamMap(queryParams)

    val serviceParam =
      params.validatedParam("service", validValues=Set("wms"))

    val requestParam =
      params.validatedParam("request", validValues=Set("getcapabilities", "getmap"))

    val firstStageValidation =
      (serviceParam, requestParam).mapN { case (a, b) => b }

    firstStageValidation.andThen {
      case "getcapabilities" =>
        GetCapabilities.build(params)
      case "getmap" =>
        GetMap.build(params)
    }
  }
}
