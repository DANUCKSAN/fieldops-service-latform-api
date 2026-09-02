import AppKit
import Foundation

guard CommandLine.arguments.count == 3 else {
    fputs("Usage: render_linkedin_auth_graphic <input.svg> <output.png>\n", stderr)
    exit(2)
}

let input = URL(fileURLWithPath: CommandLine.arguments[1])
let output = URL(fileURLWithPath: CommandLine.arguments[2])
let targetSize = NSSize(width: 1200, height: 1500)

guard let source = NSImage(contentsOf: input) else {
    fputs("Unable to read SVG input.\n", stderr)
    exit(1)
}

let rendered = NSImage(size: targetSize)
rendered.lockFocus()
NSGraphicsContext.current?.imageInterpolation = .high
source.draw(
    in: NSRect(origin: .zero, size: targetSize),
    from: NSRect(origin: .zero, size: source.size),
    operation: .sourceOver,
    fraction: 1
)
rendered.unlockFocus()

guard let tiff = rendered.tiffRepresentation,
      let bitmap = NSBitmapImageRep(data: tiff),
      let png = bitmap.representation(using: .png, properties: [:]) else {
    fputs("Unable to encode PNG output.\n", stderr)
    exit(1)
}

try png.write(to: output)
