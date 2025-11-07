import React from 'react'
import ReactDOM from 'react-dom/client'
import {ApolloProvider} from '@apollo/client/react'
import {BrowserRouter} from 'react-router-dom'
import App from './App.tsx'
import {apolloClient} from './lib/apollo-client.ts'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
        <ApolloProvider client={apolloClient}>
            <BrowserRouter>
                <App/>
            </BrowserRouter>
        </ApolloProvider>
    </React.StrictMode>,
)