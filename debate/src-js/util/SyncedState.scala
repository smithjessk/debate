package debate.util

import debate._

import scalajs.js.typedarray.ArrayBuffer

import org.scalajs.dom.WebSocket
import org.scalajs.dom.raw.Blob
import org.scalajs.dom.raw.CloseEvent
import org.scalajs.dom.raw.MessageEvent
import org.scalajs.dom.raw.Event
import org.scalajs.dom.raw.FileReader

import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.StateSnapshot

import monocle.macros.Lenses
import monocle.macros.GenPrism

import cats.implicits._

/** HOC for websocket connections that manage a state variable synced between
  * multiple clients.
  *
  * @param sendRequest
  * @param readResponse
  * @param getRequestFromState
  */
class SyncedState[Request, State](
    sendRequest: (WebSocket, Request) => Callback,
    readResponse: ArrayBuffer => State,
    getRequestFromState: State => Request
) {

  sealed trait FullState
  @Lenses case class ConnectedState(socket: WebSocket, state: State) extends FullState
  case class WaitingState(socket: WebSocket) extends FullState

  sealed trait Context

  case class Disconnected(reconnect: Callback, reason: String)
      extends FullState with Context
  case object Connecting extends FullState with Context

  case class Waiting(sendRequest: Request => Callback) extends Context
  case class Connected(state: StateSnapshot[State], sendRequest: Request => Callback) extends Context

  object FullState {
    val connectedState = GenPrism[FullState, ConnectedState]
  }

  case class Props(
      websocketURI: String,
      didUpdate: (State, State) => Callback,
      render: Context => VdomElement
  )

  class Backend(scope: BackendScope[Props, FullState]) {

    def connect(props: Props): Callback = scope.state >>= {
      case ConnectedState(_, _) | WaitingState(_) =>
        Callback(System.err.println("Already connected."))
      case Disconnected(_, _) | Connecting =>
        scope.setState(Connecting) >> Callback {
          val socket = new WebSocket(props.websocketURI)
          socket.onopen = { (_: Event) =>
            (scope.setState(WaitingState(socket))).runNow()
          }
          socket.onerror = { (event: Event) =>
            val msg = s"WebSocket connection failure. Error: ${event}"
            System.err.println(msg)
            scope.setState(Disconnected(connect(props), msg)).runNow()
          }
          // socket.onmessage = { (event: MessageEvent) =>
          //   props.onMessage(send, responseFromString(event.data.toString)).runNow()
          // }
          socket.onmessage = { (event: MessageEvent) =>
            val reader = new FileReader();
            reader.addEventListener(
              "loadend",
              (_: Event) => {
                val message = readResponse(reader.result.asInstanceOf[ArrayBuffer])
                scope.modState {
                  case WaitingState(s) => ConnectedState(s, message)
                  case ConnectedState(s, _) => ConnectedState(s, message)
                  case x => x
                }.runNow()
              }
            );
            // println(event.data) // XXX
            reader.readAsArrayBuffer(event.data.asInstanceOf[Blob]);
          }
          socket.onclose = { (event: CloseEvent) =>
            val cleanly = if (event.wasClean) "cleanly" else "uncleanly"
            val msg =
              s"WebSocket connection closed $cleanly with code ${event.code}. reason: ${event.reason}"
            if (!event.wasClean) {
              System.err.println(msg)
            }
            // will trigger a warning if closure was done with unmount i think
            scope.setState(Disconnected(connect(props), msg)).runNow()
          }
        }
    }

    def close(s: FullState): Callback = s match {
      case Disconnected(_, _)        => Callback.empty
      case Connecting                => Callback.empty
      case WaitingState(socket)      => Callback(socket.close(1000))
      case ConnectedState(socket, _) => Callback(socket.close(1000))
    }

    def render(props: Props, s: FullState) =
      props.render(
        s match {
          case x @ Disconnected(_, _) => (x: Context)
          case Connecting         => Connecting
          case WaitingState(s)    => Waiting(sendRequest(s, _))
          case ConnectedState(socket, state) =>
            Connected(
              StateSnapshot(state)(
                (stateOpt: Option[State], cb: Callback) => stateOpt.foldMap(
                  state => sendRequest(socket, getRequestFromState(state))
                ) >> cb
              ),
              sendRequest(socket, _)
            ): Context
        }
      )
  }

  val Component = ScalaComponent
    .builder[Props]("WebSocket")
    .initialState(Connecting: FullState)
    .renderBackend[Backend]
    .componentDidMount($ => $.backend.connect($.props))
    .componentWillUnmount($ => $.backend.close($.state))
    .componentDidUpdate($ =>
      ($.prevState, $.currentState) match {
        case (ConnectedState(_, prevS), ConnectedState(_, curS)) =>
          $.currentProps.didUpdate(prevS, curS)
        case _ => Callback.empty
      }
    )
    .build

  def make(
    websocketURI: String,
    didUpdate: (State, State) => Callback = (_, _) => Callback.empty)(
    render: Context => VdomElement,
  ) = Component(Props(websocketURI, didUpdate, render))
}
