import akka.actor.ActorSystem
import akka.event.{LoggingAdapter, Logging}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{MediaTypes, HttpEntity, HttpResponse, HttpRequest}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{RouteResult, RequestContext}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.io.IOException
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.math._
import spray.json.DefaultJsonProtocol
import spray.json._
import DefaultJsonProtocol._
import scala.concurrent.duration._
import scala.util.Try


case class Weapon(tag :String, name :String)
object Weapon {
  implicit val weaponFmt = jsonFormat2(Weapon.apply)
}

case class WeaponCondition(tag :String, name :String)
object WeaponCondition {
  implicit val weaponConditionFmt = jsonFormat2(WeaponCondition.apply)
}

trait Service {
  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: Materializer

  def config: Config
  val logger: LoggingAdapter

  val routes = {
    logRequestResult("akka-http-microservice") {
      path("test") {
        get {
          complete {
            "serve up angular application"
          }
        }
      } ~
      path("listGuns") {
        get {
          complete {
            getWeapons
          }
        }
      } ~
      path("listConditions") {
        get {
          complete {
            getWeaponConditions
          }
        }
      } ~
      path("listSkins") {
        get { request =>
            val weapon :String = request.request.getUri().query.get("weapon_name").getOrElse("AK-47")
            println("Got input: " + weapon)
            //request.complete {
              getSkinsNames(request, weapon)
            //}
        }
      }
    }
  }

  def getWeaponConditions = List(
    WeaponCondition("tag_WearCategory0", "Factory New"),
    WeaponCondition("tag_WearCategory1", "Minimal Wear"),
    WeaponCondition("tag_WearCategory2", "Field-Tested"),
    WeaponCondition("tag_WearCategory3", "Well-Worn"),
    WeaponCondition("tag_WearCategory4", "Battle-Scarred")
  )

  def getWeapons = List(
    Weapon("tag_weapon_ak47", "AK-47"),
    Weapon("tag_weapon_aug", "AUG"),
    Weapon("tag_weapon_awp", "AWP"),
    Weapon("tag_weapon_bayonet", "Bayonet"),
    Weapon("tag_weapon_knife_survival_bowie", "Bowie Knife"),
    Weapon("tag_weapon_knife_butterfly", "Butterfly Knife"),
    Weapon("tag_weapon_cz75a", "CZ75-Auto"),
    Weapon("tag_weapon_deagle", "Desert Eagle"),
    Weapon("tag_weapon_elite", "Dual Berettas"),
    Weapon("tag_weapon_knife_falchion", "Falchion Knife"),
    Weapon("tag_weapon_famas", "FAMAS"),
    Weapon("tag_weapon_fiveseven", "Five-SeveN"),
    Weapon("tag_weapon_knife_flip", "Flip Knife"),
    Weapon("tag_weapon_g3sg1", "G3SG1"),
    Weapon("tag_weapon_galilar", "Galil AR"),
    Weapon("tag_weapon_glock", "Glock-18"),
    Weapon("tag_weapon_knife_gut", "Gut Knife"),
    Weapon("tag_weapon_knife_tactical", "Huntsman Knife"),
    Weapon("tag_weapon_knife_karambit", "Karambit"),
    Weapon("tag_weapon_m249", "M249"),
    Weapon("tag_weapon_m4a1_silencer", "M4A1-S"),
    Weapon("tag_weapon_m4a1", "M4A4"),
    Weapon("tag_weapon_knife_m9_bayonet", "M9 Bayonet"),
    Weapon("tag_weapon_mac10", "MAC-10"),
    Weapon("tag_weapon_mag7", "MAG-7"),
    Weapon("tag_weapon_mp7", "MP7"),
    Weapon("tag_weapon_mp9", "MP9"),
    Weapon("tag_weapon_negev", "Negev"),
    Weapon("tag_weapon_nova", "Nova"),
    Weapon("tag_weapon_hkp2000", "P2000"),
    Weapon("tag_weapon_p250", "P250"),
    Weapon("tag_weapon_p90", "P90"),
    Weapon("tag_weapon_bizon", "PP-Bizon"),
    Weapon("tag_weapon_revolver", "R8 Revolver"),
    Weapon("tag_weapon_sawedoff", "Sawed-Off"),
    Weapon("tag_weapon_scar20", "SCAR-20"),
    Weapon("tag_weapon_sg556", "SG 553"),
    Weapon("tag_weapon_knife_push", "Shadow Daggers"),
    Weapon("tag_weapon_ssg08", "SSG 08"),
    Weapon("tag_weapon_tec9", "Tec-9"),
    Weapon("tag_weapon_ump45", "UMP-45"),
    Weapon("tag_weapon_usp_silencer", "USP-S"),
    Weapon("tag_weapon_xm1014", "XM1014")
  )

  def getWeaponByName(weaponName :String) :Weapon = {
    getWeapons.filter(w => w.name == weaponName).headOption getOrElse getWeapons(0)
  }

  def getSkinsNames(request :RequestContext, weaponName :String) :Future[RouteResult] = {

    val skinsUrl :String = "/market/search?appid=730&category_730_Weapon[]=" +
      getWeaponByName(weaponName).tag + "&category_730_Exterior[]=tag_WearCategory0"

    makeExternalCall(skinsUrl, {
      response =>
        Unmarshal(response.entity).to[String].flatMap { x =>
            //request.complete(x)
            //request.complete(x + "\nHERE:" + extractSkinNames(x))
            request.complete(extractSkinNames(x))
        }
        //request.complete("hello")
    })
  }

  def makeExternalCall(url :String, callback :((HttpResponse) => Future[RouteResult])) :Future[RouteResult] = {
    steamRequest(RequestBuilding.Get(url)).flatMap { response =>
      callback(response)
    }
  }

  def extractSkinNames(input :String, matches :List[String] = List()) :List[String] = {
    println(s"iteration: " + input.length + s", starting at 0, with " + matches.length)
    input.indexOf("market_listing_item_name\"") match {
      case -1 => matches
      case x => {
        val nameStart = input.indexOf(">", x) + 1
        val nameEnd = input.indexOf("<", nameStart)
        val skinName = input.substring(nameStart, nameEnd)
        println(s"skinName: $skinName, ($nameStart,$nameEnd) " + matches.length)
        extractSkinNames(input.substring(nameEnd), matches ++ List(skinName))
      }
    }
  }

  lazy val steamFlow: Flow[HttpRequest, HttpResponse, Any] = Http().outgoingConnection("steamcommunity.com", 80)
  def steamRequest(request: HttpRequest): Future[HttpResponse] = Source.single(request).via(steamFlow).runWith(Sink.head)
}

object AkkaHttpMicroservice extends App with Service {
  override implicit val system = ActorSystem()
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorMaterializer()

  override val config = ConfigFactory.load()
  override val logger = Logging(system, getClass)

  Http().bindAndHandle(routes, config.getString("http.interface"), config.getInt("http.port"))
}
