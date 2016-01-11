TinyDNSSD
=========

TinyDNSSD is a Java library for sending and receiving Multicast DNS packets to perform DNS Service
Discovery.

From Android 4.1, service discovery can be performed using the [NsdManager]. However some use cases
make use of the DNS TXT record to efficiently add extra key-value metadata, and Android has a
[bug] that causes the returned data to always be empty. This library can be used to work around this bug.

This library has an Android specific class, the `DiscoverResolver`, which still uses `NsdManager` to
leverage the system's already running discovery service, but will send another unicast query packet
in order to retrieve the TXT record. It also simplifies the API usage compared to calling `NsdManager`
directly.

Another feature is _service visibility debouncing_: sometimes [NsdManager] will signal
`onServiceLost()` then shortly afterwards report the same service in `onServiceFound()`. With this
library a tolerance to this can be configured - removed services are not notified to the client
until some time elapses without the service reappearing.

Usage
-----

```java
DiscoverResolver resolver = new DiscoverResolver(context, "_androidtvremote._tcp",
    new DiscoverResolver.Listener() {
        @Override
        public void onServicesChanged(Map<String, MDNSDiscover.Result> services) {
            for (MDNSDiscover.Result result : services.values()) {
                // access the Bluetooth MAC from the TXT record
                String mac = result.txt.dict.get("bt");
                String name = result.srv.fqdn;
                Log.d(TAG, name + " -> " + mac);
            }
        }
    });
resolver.start();
```

License
-------

    Copyright (c) 2015 YouView TV Ltd
    
    Permission is hereby granted, free of charge, to any person obtaining a copy of
    this software and associated documentation files (the "Software"), to deal in
    the Software without restriction, including without limitation the rights to
    use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
    the Software, and to permit persons to whom the Software is furnished to do so,
    subject to the following conditions:
    
    The above copyright notice and this permission notice shall be included in all
    copies or substantial portions of the Software.
    
    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
    FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
    COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
    IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
    CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

[bug]: https://code.google.com/p/android/issues/detail?id=136099
[NsdManager]: http://developer.android.com/reference/android/net/nsd/NsdManager.html